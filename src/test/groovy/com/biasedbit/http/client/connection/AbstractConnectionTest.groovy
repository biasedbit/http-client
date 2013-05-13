package com.biasedbit.http.client.connection

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
import java.util.concurrent.Executors

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import static org.jboss.netty.channel.Channels.fireExceptionCaught
import static org.jboss.netty.channel.Channels.fireMessageReceived
import static org.jboss.netty.handler.codec.http.HttpMethod.GET
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
abstract class AbstractConnectionTest extends Specification {

  def host              = "biasedbit.com"
  def port              = 80
  def listener          = Mock(ConnectionListener)
  def timeoutController = Mock(TimeoutController)
  def connection        = createConnection(host, port, listener, timeoutController, null)
  def outgoingMessages  = []

  private DecoderEmbedder decoder

  protected Channel channel() { decoder.pipeline.channel }

  protected void connectConnection() { decoder = new DecoderEmbedder(connection) }

  protected void setupDownstreamPipelineMessageTrap() {
    decoder.pipeline.addFirst("trap", new SimpleChannelDownstreamHandler() {
      @Override void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        super.writeRequested(ctx, e)
        AbstractConnectionTest.this.outgoingMessages << e.message
      }
    })
  }

  protected <T> RequestContext<T> createRequest(def options = [:]) {
    def processor = options.get("processor", new DiscardProcessor())
    def request = new RequestContext(host, port, 100, new DefaultHttpRequest(HTTP_1_1, GET, "/"), processor)
    request.dataSinkListener = options["sink"]

    request
  }

  protected abstract def createConnection(String host, int port, ConnectionListener listener,
                                          TimeoutController timeoutController, Executor executor);

  def setup() {
    connectConnection()
    setupDownstreamPipelineMessageTrap()
  }

  def cleanup() { if (decoder != null) decoder.finish() }

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
    given: "a non-connected connection"
    connection = createConnection(host, port, listener, timeoutController, null)

    when: "it connects"
    connectConnection()

    then: "the listener receives a connection open event"
    1 * listener.connectionOpened(connection)
  }

  def "it does not signal the listener that a connection has been opened if it's terminated before"() {
    given: connection.terminate(RequestFuture.SHUTTING_DOWN)
    when: connectConnection()
    then: 0 * listener.connectionOpened(connection)
  }

  def "it signals the listener a connection has failed to open if it receives a channel closed even before opening"() {
    given: "a connection that is opening"
    connection = createConnection(host, port, listener, timeoutController, null)

    when: connection.channelClosed(null, null)
    then: 1 * listener.connectionFailed(connection)
  }

  def "it signals the listener a connection has been terminated if an exception occurs"() {
    when: fireExceptionCaught(decoder.pipeline.channel, new Exception("kaboom"))
    then: 1 * listener.connectionTerminated(connection)
  }

  def "it signals the listener a connection has been terminated with requests to restore if an exception occurs"() {
    given: def request = createRequest()
    when: assert connection.execute(request)
    and: fireExceptionCaught(channel(), new Exception("kaboom"))
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

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the listener is notified of the connection termination"
    1 * listener.connectionTerminated(connection)
  }

  def "it signals a connection termination if the response is not keep-alive"() {
    given: "a request"
    def request = createRequest()

    when: "the request is executed"
    connection.execute(request)

    and: "a successful non-keep-alive response is received"
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    response.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
    fireMessageReceived(channel(), response)

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the listener is notified of the connection termination"
    1 * listener.connectionTerminated(connection)
  }

  def "it delegates the network write to the executor when configured with one"() {
    given: "a connection configured to use an executor"
    def executor = Mock(Executor)
    connection = createConnection(host, port, listener, timeoutController, executor)
    connectConnection()

    and: "a request"
    def request = createRequest()

    when: "the request is executed"
    connection.execute(request)

    then: "a task is submitted to the executor"
    1 * executor.execute(_)
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
    request.future.hasSuccessfulResponse()

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

  // terminate

  def "-terminate causes the currently executing request to be cancelled"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    when: "the connection is terminated with cause SHUTTING_DOWN"
    connection.terminate(RequestFuture.SHUTTING_DOWN)

    then: "the connection listener is notified"
    1 * listener.connectionTerminated(connection)

    and: "the request is marked as failed with cause SHUTTING_DOWN"
    request.future.done
    request.future.cause == RequestFuture.SHUTTING_DOWN
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

  // execution submission

  def "-execute returns 'false' if the connection is not yet established"() {
    given: "a connection not yet established"
    connection = createConnection(host, port, listener, timeoutController, null)

    and: "a request to execute"
    def request = createRequest()

    expect: "it to reject execution"
    !connection.execute(request)
  }

  def "-execute returns 'false' if the connection has already been terminated"() {
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

  // network write failure

  def "it immediately fails the request and signals finished request if the network write fails"() {
    given: "a channel that will raise exception when writing the request to the server"
    channel().pipeline.addLast("bomber", new SimpleChannelDownstreamHandler() {
      @Override void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        throw new Exception("kaboom")
      }
    })

    and: "a request to execute"
    def request = createRequest()

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the request will be marked as failed"
    request.future.done
    !request.future.successful
    request.future.cause != null
    request.future.cause.message.endsWith("kaboom") // endsWith because this is a wrapped exception

    and: "the connection listener will have been notified of a finished request"
    1 * listener.requestFinished(connection, request)

    and: "the connection will still be marked as being available"
    connection.available
  }

  def "it immediately fails the request and signals finished request if the network write fails using an executor"() {
    given: "a connection configured to use an executor"
    def executor = Executors.newSingleThreadExecutor()
    connection = createConnection(host, port, listener, timeoutController, executor)
    connectConnection()

    and: "the network channel will raise exception when writing the request to the server"
    channel().pipeline.addLast("bomber", new SimpleChannelDownstreamHandler() {
      @Override void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        throw new Exception("kaboom")
      }
    })

    and: "a request to execute"
    def request = createRequest()

    when: "the connection executes the request"
    assert connection.execute(request)

    and: "a client waits for the future associated with the request to complete"
    request.future.await(200)

    then: "the request will be marked as failed"
    request.future.done
    !request.future.successful
    request.future.cause != null
    request.future.cause.message.endsWith("kaboom") // endsWith because this is a wrapped exception

    and: "the connection listener will have been notified of a finished request"
    1 * listener.requestFinished(connection, request)

    and: "the connection will still be marked as being available"
    connection.available
  }
}
