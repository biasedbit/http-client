package com.biasedbit.http.client.connection

import com.biasedbit.http.client.future.DataSinkListener
import com.biasedbit.http.client.future.RequestFuture
import com.biasedbit.http.client.timeout.TimeoutController
import org.jboss.netty.handler.codec.http.DefaultHttpChunk
import org.jboss.netty.handler.codec.http.DefaultHttpChunkTrailer
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.util.CharsetUtil

import java.util.concurrent.Executor

import static org.jboss.netty.buffer.ChannelBuffers.EMPTY_BUFFER
import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import static org.jboss.netty.channel.Channels.fireMessageReceived
import static org.jboss.netty.channel.Channels.fireWriteComplete
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.CONTINUE
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultConnectionSpec extends AbstractConnectionTest {

  @Override protected Connection createConnection(String host, int port, ConnectionListener listener,
                                                  TimeoutController timeoutController, Executor executor) {
    return new DefaultConnection("${host}:${port}", host, port, listener, timeoutController, executor)
  }

  // execution submission

  def "-execute returns 'false' if another request is being executed"() {
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

  // data sink

  def "-isConnected returns true when the channel is connected and the connection has not been terminated"() {
    expect: connection.connected
  }

  def "-isConnected returns false when the channel is not yet connected"() {
    given: "a connection not yet established"
    connection = createConnection(host, port, listener, timeoutController, null)

    expect: !connection.connected
  }

  def "-isConnected returns false when the connection has already been terminated"() {
    given: "terminated connection"
    connection.terminate(RequestFuture.SHUTTING_DOWN)

    expect: !connection.connected
  }

  def "-disconnect terminates the connection listener and fails current, even if its idempotent"() {
    given: "an idempotent request executing"
    def request = createRequest()
    assert connection.execute(request)

    when: "disconnection is issued"
    connection.disconnect()

    then: "the connection listener is notified"
    1 * listener.connectionTerminated(connection)

    and: "the request's future is marked as terminated with CANCELLED cause"
    request.future.done
    request.future.cause == RequestFuture.CANCELLED
  }

  def "-sendData ignores calls if connection is not yet established"() {
    given: "a connection not yet established"
    connection = createConnection(host, port, listener, timeoutController, null)

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

    then: "no data is sent down the pipeline, only the request"
    outgoingMessages[0] instanceof DefaultHttpRequest
    outgoingMessages.size() == 1
  }

  def "-sendData ignores calls if data is null and 'last' flag is not set"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    and: "a continuation response has been received"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))

    when: "null data is fed to the connection"
    connection.sendData(null, false)

    then: "no data is sent down the pipeline, only the request"
    outgoingMessages[0] instanceof DefaultHttpRequest
    outgoingMessages.size() == 1
  }

  def "-sendData ignores calls if data has no readable bytes and 'last' flag is not set"() {
    given: "a connection executing a request"
    def request = createRequest()
    assert connection.execute(request)

    and: "a continuation response has been received"
    fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))

    when: "null data is fed to the connection"
    connection.sendData(EMPTY_BUFFER, false)

    then: "no data is sent down the pipeline, only the request"
    outgoingMessages[0] instanceof DefaultHttpRequest
    outgoingMessages.size() == 1
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
    with(outgoingMessages[1] as DefaultHttpChunk) { content.toString(CharsetUtil.UTF_8) == "biasedbit.com" }
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
    with(outgoingMessages[1] as DefaultHttpChunk) { content.toString(CharsetUtil.UTF_8) == "biasedbit.com" }

    and: "a trailer chunk is also sent down the pipeline"
    outgoingMessages[2] instanceof DefaultHttpChunkTrailer
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
}
