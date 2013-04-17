package com.biasedbit.http.client.connection

import com.biasedbit.http.client.future.DataSinkListener
import com.biasedbit.http.client.future.RequestFuture
import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.client.processor.ResponseProcessor
import com.biasedbit.http.client.timeout.TimeoutController
import com.biasedbit.http.client.util.RequestContext
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelDownstreamHandler
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder
import org.jboss.netty.handler.codec.http.*
import org.jboss.netty.util.CharsetUtil
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executor

import static org.jboss.netty.buffer.ChannelBuffers.EMPTY_BUFFER
import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import static org.jboss.netty.channel.Channels.*
import static org.jboss.netty.handler.codec.http.HttpMethod.GET
import static org.jboss.netty.handler.codec.http.HttpMethod.POST
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultConnectionSpec extends Specification {

  def host              = "biasedbit.com"
  def port              = 80
  def listener          = Mock(ConnectionListener)
  def timeoutController = Mock(TimeoutController)
  def executor          = Mock(Executor)
  def connection        = new DefaultConnection("${host}:${port}", host, port, listener, timeoutController, executor)
  def decoder           = new DecoderEmbedder(connection)
  def outgoingMessages  = []

  def setup() { setupDownstreamPipelineMessageTrap() }
  def cleanup() { decoder.finish() }

  def "it preserves creation properties"() {
    expect:
    connection.id == "${host}:${port}"
    connection.host == host
    connection.port == port
  }

  def "it doesn't accept null requests"() {
    when: connection.execute(null)
    then: thrown(IllegalArgumentException)
  }

  def "it signals the listener that a connection has been opened"() {
    when: decoder = new DecoderEmbedder(connection) // fires connection open events up the chain
    then: 1 * listener.connectionOpened(connection)
  }

  def "it does not signal the listener that a connection has been opened if it's terminated before"() {
    given: connection.terminate(RequestFuture.SHUTTING_DOWN)
    when: decoder = new DecoderEmbedder(connection)
    then: 0 * listener.connectionOpened(connection)
  }

  def "it signals the listener a connection has failed to open if it receives a channel closed even before opening"() {
    given: "a connection that is opening"
    // We need to create another connection here because this test assumes the common case (an open connection)
    connection = new DefaultConnection("${host}:${port}", host, port, listener, timeoutController, executor)

    when: connection.channelClosed(null, null)
    then: 1 * listener.connectionFailed(connection)
  }

  def "it signals the listener a connection has been terminated if an exception occurs"() {
    when: fireExceptionCaught(decoder.pipeline.channel, new Exception("kaboom"))
    then: 1 * listener.connectionTerminated(connection)
  }

  def "it signals the listener a connection has been terminated with requests to restore if an exception occurs"() {
    given:
    def request = createRequest()
    when: assert connection.execute(request)
    and: fireExceptionCaught(decoder.pipeline.channel, new Exception("kaboom"))
    then: 1 * listener.connectionTerminated(connection, [request])
  }

  def "it discards responses received when there is no current request"() {
    when: fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))
    then: 0 * listener.requestFinished(connection, _)
  }

  def "it signals a connection termination if the request is not keep-alive"() {
    given: "a non-keepalive request"
    def request = createRequest()
    request.request.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)

    when: "the request is executed"
    connection.execute(request)

    and: "a successful response is received"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, OK))

    then: "the listener is notified of the connection termination"
    1 * listener.connectionTerminated(connection)
  }

  def "it signals a connection termination if the response is not keep-alive"() {
    given: "a non-keepalive request"
    def request = createRequest()

    when: "the request is executed"
    connection.execute(request)

    and: "a successful non-keep-alive response is received"
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    response.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
    fireMessageReceived(channel(), response)

    then: "the listener is notified of the connection termination"
    1 * listener.connectionTerminated(connection)
  }

  // response processor

  def "it fails the request future if an exception is raised when querying the processor's -willProcess"() {
    given: "a response processor that will raise exception when it's -willProcess method is called"
    def processor = Mock(ResponseProcessor) {
      willProcessResponse(_) >> { throw new Exception("kaboom") }
    }

    and: "a request"
    def request = createRequest(processor: processor)

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a response is received from the server"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, OK))

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the future is marked as failed with the correct exception"
    request.future.cause != null
    request.future.cause.message == "kaboom"

    and: "the connection listener is notified that a request has finished"
    1 * listener.requestFinished(connection, request)
  }

  def "it completes the request without content if the response processor does not accept the response"() {
    given: "a response processor that will reject the response"
    def processor = Mock(ResponseProcessor) { willProcessResponse(_) >> false }

    and: "a request"
    def request = createRequest(processor: processor)

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a response is received from the server"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, OK))

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the future is marked as complete without result"
    request.future.processedResult == null
    request.future.successfulResponse

    and: "the connection listener is notified that a request has finished"
    1 * listener.requestFinished(connection, request)
  }

  def "it unlocks the future with the result if the response processor accepts a non-chunked response"() {
    given: "a response processor that will accept the response"
    def processor = Mock(ResponseProcessor) {
      willProcessResponse(_) >> true
      getProcessedResponse() >> "biasedbit.com"
    }

    and: "a request"
    def request = createRequest(processor: processor)

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a response is received from the server"
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain")
    response.content = copiedBuffer("biasedbit.com", CharsetUtil.UTF_8)
    fireMessageReceived(channel(), response)

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the processor received data"
    1 * processor.addLastData(_) >> { it[0].toString(CharsetUtil.UTF_8) == "biasedbit.com" }

    and: "the future is marked as complete with the result"
    request.future.processedResult == "biasedbit.com"

    and: "the connection listener is notified that a request has finished"
    1 * listener.requestFinished(connection, request)
  }

  def "it unlocks the future with the result if the response processor accepts a chunked response"() {
    given: "a response processor that will accept the response"
    def processor = Mock(ResponseProcessor) {
      willProcessResponse(_) >> true
      getProcessedResponse() >> "biasedbit.com"
    }

    and: "a request"
    def request = createRequest(processor: processor)

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a chunked response is received from the server"
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain")
    response.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED)
    response.chunked = true
    fireMessageReceived(channel(), response)

    and: "two more chunks with data and a trailer chunk are received from the server"
    fireMessageReceived(channel(), new DefaultHttpChunk(copiedBuffer("biasedbit", CharsetUtil.UTF_8)))
    fireMessageReceived(channel(), new DefaultHttpChunk(copiedBuffer(".com", CharsetUtil.UTF_8)))
    fireMessageReceived(channel(), new DefaultHttpChunkTrailer())

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the processor received a piece of data for each chunk"
    1 * processor.addData(_) >> { it[0].toString(CharsetUtil.UTF_8) == "biasedbit" }
    1 * processor.addData(_) >> { it[0].toString(CharsetUtil.UTF_8) == ".com" }
    1 * processor.addLastData(_) >> { it[0].readableBytes() == 0 } // trailing chunk

    and: "the future is marked as complete with the result"
    request.future.processedResult == "biasedbit.com"

    and: "the connection listener is notified that a request has finished"
    1 * listener.requestFinished(connection, request)
  }

  def "it does not feed data to the response processor if the response content is to be discarded"() {
    given: "a response processor that will reject the response"
    def processor = Mock(ResponseProcessor) { willProcessResponse(_) >> false }

    and: "a request"
    def request = createRequest(processor: processor)

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a chunked response is received from the server"
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain")
    response.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED)
    response.chunked = true
    fireMessageReceived(channel(), response)

    and: "two more chunks with data and a trailer chunk are received from the server"
    fireMessageReceived(channel(), new DefaultHttpChunk(copiedBuffer("biasedbit", CharsetUtil.UTF_8)))
    fireMessageReceived(channel(), new DefaultHttpChunk(copiedBuffer(".com", CharsetUtil.UTF_8)))
    fireMessageReceived(channel(), new DefaultHttpChunkTrailer())

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the processor has not received any calls to -add*Data()"
    0 * processor.addData(_)
    0 * processor.addLastData(_)
  }

  @Unroll
  def "it unlocks the request future with an exception when -#method raises exception"() {
    given: "a response processor that will raise an exception on -add*Data()"
    def processor = Mock(ResponseProcessor) {
      willProcessResponse(_) >> true
      "${method}"(_) >> { throw new Exception("kaboom") }
    }

    and: "a request"
    def request = createRequest(processor: processor)

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a chunked response is received from the server"
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain")
    response.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED)
    response.chunked = true
    fireMessageReceived(channel(), response)

    and: "a chunk with data and a trailer chunk are received from the server"
    fireMessageReceived(channel(), new DefaultHttpChunk(copiedBuffer("biasedbit.com", CharsetUtil.UTF_8)))
    fireMessageReceived(channel(), new DefaultHttpChunkTrailer())

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the future is marked as failed with the correct exception"
    request.future.cause != null
    request.future.cause.message == "kaboom"

    and: "the connection listener is notified that a request has finished"
    1 * listener.requestFinished(connection, request)

    where: method << ["addData", "addLastData"]
  }

  // cancel

  def "it immediately signals request finished if a future has already been cancelled"() {
    given: "a request queued by the client"
    def request = createRequest()

    when: "the request is cancelled before being fed to the connection"
    request.future.cancel()

    and: "it is finally fed to the connection"
    assert connection.execute(request)

    then: "the connection immediately signals request finished"
    1 * listener.requestFinished(connection, request)

    and: "the connection remains available"
    connection.available
  }

  def "it terminates the connection when a request is cancelled"() {
    given: "a request fed to the connection"
    def context = createRequest()
    assert connection.execute(context)

    when: "the request is cancelled"
    context.future.cancel()

    then: "the connection listener is notified of the termination"
    1 * listener.connectionTerminated(connection)

    and: "the connection no longer reports itself as being available"
    !connection.available
  }

  def "it immediately fails requests if the connection returns 'false' on -isAvailable"() {
    given: "a connection currently executing a request"
    def firstRequest = createRequest()
    assert connection.execute(firstRequest)

    and: "another request to be executed with a listener"
    def secondRequest = createRequest()

    when: "the second request is fed to the connection"
    assert connection.execute(secondRequest)

    then: "the second request is immediately marked as failed with cause EXECUTION_REJECTED"
    secondRequest.future.done
    secondRequest.future.cause == RequestFuture.EXECUTION_REJECTED

    and: "the first request goes on"
    !firstRequest.future.done
  }

  def "it rejects execution if the connection is not yet established"() {
    given: "a connection not yet established"
    connection = new DefaultConnection("${host}:${port}", host, port, listener, timeoutController, executor)

    and: "a request to execute"
    def request = createRequest()

    expect: "it to reject execution"
    !connection.execute(request)
  }

  def "it rejects execution if the connection has already been terminated"() {
    given: "a terminated connection"
    connection.terminate(RequestFuture.SHUTTING_DOWN)

    and: "a request to execute"
    def request = createRequest()

    expect: "it to reject execution"
    !connection.execute(request)
  }

  def "it does not finish the request nor notifies the listener if an execution is rejected"() {
    given: "a terminated connection"
    connection.terminate(RequestFuture.SHUTTING_DOWN)

    when: "a request is fed to it"
    def request = createRequest()
    connection.execute(request)

    then: "no request completion to be signalled to the connection listener"
    0 * listener.requestFinished(connection, _)

    then: "the request future to remain intact"
    !request.future.done
  }

  // data sink

  def "-isConnected returns true when the channel is connected and the connection has not been terminated"() {
    expect: connection.connected
  }

  def "-isConnected returns false when the channel is not yet connected"() {
    given: "a connection not yet established"
    connection = new DefaultConnection("${host}:${port}", host, port, listener, timeoutController, executor)

    expect: !connection.connected
  }

  def "-isConnected returns false when the connection has already been terminated"() {
    given: "terminated connection"
    connection.terminate(RequestFuture.SHUTTING_DOWN)

    expect: !connection.connected
  }

  def "-disconnect terminates the connection listener and includes idempotent requests to restore"() {
    given: "an idempotent request executing"
    def request = createRequest()
    assert connection.execute(request)

    when: "disconnection is issued"
    connection.disconnect()

    then: "the connection listener is notified"
    1 * listener.connectionTerminated(connection, [request])

    and: "the request's future is not marked as terminated"
    !request.future.done
  }

  def "-disconnect terminates the connection listener and fails non-idempotent requests"() {
    given: "an idempotent request executing"
    def request = new RequestContext(host, port, 100,
        new DefaultHttpRequest(HTTP_1_1, POST, "/"), new DiscardProcessor())
    assert connection.execute(request)

    when: "disconnection is issued"
    connection.disconnect()

    then: "the connection listener is notified"
    1 * listener.connectionTerminated(connection)

    and: "the request's future is marked as terminated with CANCELLED cause"
    request.future.done
    request.future.cause == RequestFuture.CANCELLED
  }

  def "-disconnect terminates the connection and restores non-idempotent requests when configured to do so"() {
    given: "a connection configured to restore non-idempotent operations"
    connection.restoreNonIdempotentOperations = true

    and: "a non-idempotent request executing"
    def request = new RequestContext(host, port, 100,
        new DefaultHttpRequest(HTTP_1_1, POST, "/"), new DiscardProcessor())
    assert connection.execute(request)

    when: "disconnection is issued"
    connection.disconnect()

    then: "the connection listener is notified"
    1 * listener.connectionTerminated(connection, [request])

    and: "the request's future is not marked as terminated"
    !request.future.done
  }

  def "-sendData ignores calls if connection is not yet established"() {
    given: "a connection not yet established"
    connection = new DefaultConnection("${host}:${port}", host, port, listener, timeoutController, executor)

    when: "data is fed to it"
    connection.sendData(copiedBuffer("biasedbit.com", CharsetUtil.UTF_8), false)

    then: "no data is sent down the pipeline"
    outgoingMessages.empty
  }

  def "-sendData ignores calls if no request is currently executing"() {
    when: "data is fed to it"
    connection.sendData(copiedBuffer("biasedbit.com", CharsetUtil.UTF_8), false)

    then: "no data is sent down the pipeline"
    outgoingMessages.empty
  }

  def "-sendData ignores calls if no continuation response has been received"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    when: "data is fed to it"
    connection.sendData(copiedBuffer("biasedbit.com", CharsetUtil.UTF_8), false)

    then: "no data is sent down the pipeline"
    outgoingMessages.empty
  }

  def "-sendData ignores calls if data is null and 'last' flag is not set"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    and: "a continuation response has been received"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))

    when: "null data is fed to the connection"
    connection.sendData(null, false)

    then: "no data is sent down the pipeline"
    outgoingMessages.empty
  }

  def "-sendData ignores calls if data has no readable bytes and 'last' flag is not set"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    and: "a continuation response has been received"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))

    when: "null data is fed to the connection"
    connection.sendData(EMPTY_BUFFER, false)

    then: "no data is sent down the pipeline"
    outgoingMessages.empty
  }

  def "-sendData writes a DefaultHttpChunk with the contents of input buffer if data is not marked as last"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    and: "a continuation response has been received"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))

    when: "data is fed to the connection"
    connection.sendData(copiedBuffer("biasedbit.com", CharsetUtil.UTF_8), false)

    then: "a DefaultHttpChunk with data is sent down the pipeline"
    !outgoingMessages.empty
    with(outgoingMessages[0] as DefaultHttpChunk) { content.toString(CharsetUtil.UTF_8) == "biasedbit.com" }
  }

  def "-sendData writes a data chunk and a trailer chunk if the 'last' flag is set to true"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    and: "a continuation response has been received"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))

    when: "data is fed to the connection and marked as the last"
    connection.sendData(copiedBuffer("biasedbit.com", CharsetUtil.UTF_8), true)

    then: "a data chunk is sent down the pipeline"
    !outgoingMessages.empty
    with(outgoingMessages[0] as DefaultHttpChunk) { content.toString(CharsetUtil.UTF_8) == "biasedbit.com" }

    and: "a trailer chunk is also sent down the pipeline"
    outgoingMessages[1] instanceof DefaultHttpChunkTrailer
  }

  def "it notifies the data sink listener it's ready to send data when the 100-Continue response is received"() {
    given: def request = createRequest(sink: Mock(DataSinkListener))
    when: assert connection.execute(request)
    and: fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))
    then: 1 * request.dataSinkListener.readyToSendData(connection)
  }

  def "it does not notify the data sink listener it's ready to send if a final response is received"() {
    given: def request = createRequest(sink: Mock(DataSinkListener))
    when: assert connection.execute(request)
    and: fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, OK))
    then: 0 * request.dataSinkListener.readyToSendData(connection)
    and: 1 * listener.requestFinished(connection, request)
  }

  def "it does not notify the data sink listener when a write completes before receiving a 100-Continue response"() {
    given: def request = createRequest(sink: Mock(DataSinkListener))
    when: assert connection.execute(request)
    and: fireWriteComplete(channel(), 1000)
    then: 0 * request.dataSinkListener.writeComplete(connection, 1000)
  }

  def "it notifies the data sink whenever a write completes after receiving a 100-Continue response"() {
    given: def request = createRequest(sink: Mock(DataSinkListener))
    and: assert connection.execute(request)
    when: fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))
    and: fireWriteComplete(channel(), 1000)
    then: 1 * request.dataSinkListener.writeComplete(connection, 1000)
  }

  private Channel channel() { decoder.pipeline.channel }

  private void setupDownstreamPipelineMessageTrap() {
    decoder.pipeline.addFirst("trap", new SimpleChannelDownstreamHandler() {
      @Override void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        super.writeRequested(ctx, e)
        outgoingMessages << e.message
      }
    })
  }

  private <T> RequestContext<T> createRequest(def options = [:]) {
    def processor = options.get("processor", new DiscardProcessor())
    def request = new RequestContext<>(host, port, 100, new DefaultHttpRequest(HTTP_1_1, GET, "/"), processor)
    request.dataSinkListener = options["sink"]

    request
  }
}
