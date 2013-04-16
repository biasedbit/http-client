package com.biasedbit.http.client.connection

import com.biasedbit.http.client.future.RequestFuture
import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.client.timeout.TimeoutController
import com.biasedbit.http.client.util.RequestContext
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel.Channels
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpClientCodec
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.util.concurrent.Executor

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
  def request           = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")

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
    given: def context = new RequestContext<>(host, port, 100, request, new DiscardProcessor());
    and: assert connection.execute(context)
    when: Channels.fireExceptionCaught(decoder.pipeline.channel, new Exception("kaboom"))
    then: 1 * listener.connectionTerminated(connection, [context])
  }
}
