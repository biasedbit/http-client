package com.biasedbit.http.client.connection

import com.biasedbit.http.client.future.DataSinkListener
import com.biasedbit.http.client.future.RequestFuture
import com.biasedbit.http.client.future.RequestFutureListener
import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.client.processor.ResponseProcessor
import com.biasedbit.http.client.timeout.TimeoutController
import com.biasedbit.http.client.util.RequestContext
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.Channels
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpMethod
import spock.lang.Specification

import java.util.concurrent.Executor

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
  def request           = new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/")

  def cleanup() { decoder.finish() }

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
    when: Channels.fireExceptionCaught(decoder.pipeline.channel, new Exception("kaboom"))
    then: 1 * listener.connectionTerminated(connection)
  }

  def "it signals the listener a connection has been terminated with requests to restore if an exception occurs"() {
    given: def context = createContext()
    when: assert connection.execute(context)
    and: Channels.fireExceptionCaught(decoder.pipeline.channel, new Exception("kaboom"))
    then: 1 * listener.connectionTerminated(connection, [context])
  }

  def "it notifies the data sink listener it's ready to send data when the 100-Continue response is received"() {
    given: def context = createContext(sink: Mock(DataSinkListener))
    when: assert connection.execute(context)
    and: Channels.fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))
    then: 1 * context.dataSinkListener.readyToSendData(connection)
  }

  def "it does not notify the data sink listener it's ready to send if a final response is received"() {
    given: def context = createContext(sink: Mock(DataSinkListener))
    when: assert connection.execute(context)
    and: Channels.fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, OK))
    then: 0 * context.dataSinkListener.readyToSendData(connection)
    and: 1 * listener.requestFinished(connection, context)
  }

  def "it does not notify the data sink listener when a write completes before receiving a 100-Continue response"() {
    given: def context = createContext(sink: Mock(DataSinkListener))
    when: assert connection.execute(context)
    and: Channels.fireWriteComplete(channel(), 1000)
    then: 0 * context.dataSinkListener.writeComplete(connection, 1000)
  }

  def "it notifies the data sink whenever a write completes after receiving a 100-Continue response"() {
    given: def context = createContext(sink: Mock(DataSinkListener))
    and: assert connection.execute(context)
    when: Channels.fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))
    and: Channels.fireWriteComplete(channel(), 1000)
    then: 1 * context.dataSinkListener.writeComplete(connection, 1000)
  }

  def "it discards responses received when there is no current request"() {
    when: Channels.fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, CONTINUE))
    then: 0 * listener.requestFinished(connection, _)
  }

  def "it fails the request future if a decoding exception occurs when querying the processor's -willProcess"() {
    given:
    def processor = Mock(ResponseProcessor) {
      willProcessResponse(_) >> { throw new Exception("kaboom") }
    }
    def futureListener = new RequestFutureListener() {
      def gotException = false
      @Override void operationComplete(RequestFuture future) throws Exception {
        assert future.cause != null
        assert future.cause.message == "kaboom"
        gotException = true
      }
    }
    def context = createContext(processor: processor, futureListener: futureListener)

    when: assert connection.execute(context)
    and: Channels.fireMessageReceived(channel(), new DefaultHttpResponse(HTTP_1_1, OK))
    and: context.future.await(200)
    then: futureListener.gotException
    and: 1 * listener.requestFinished(connection, context)
  }

  private Channel channel() { decoder.pipeline.channel }

  private <T> RequestContext<T> createContext(def options = [:]) {
    def processor = options.get("processor", new DiscardProcessor())
    def request = new RequestContext<>(host, port, 100, request, processor)
    request.dataSinkListener = options["sink"]

    def futureListener = options["futureListener"]
    if (futureListener != null) request.future.addListener(futureListener)

    request
  }
}
