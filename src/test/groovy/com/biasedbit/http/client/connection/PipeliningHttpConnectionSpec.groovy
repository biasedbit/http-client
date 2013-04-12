package com.biasedbit.http.client.connection

import com.biasedbit.http.client.timeout.TimeoutController
import spock.lang.Specification

import java.util.concurrent.Executor

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class PipeliningHttpConnectionSpec extends Specification {

  PipeliningHttpConnectionFactory factory
  HttpConnectionListener          listener
  TimeoutController               timeoutController
  Executor                        executor

  def setup() {
    factory = new PipeliningHttpConnectionFactory()
    listener = Mock(HttpConnectionListener)
    timeoutController = Mock(TimeoutController)
    executor = Mock(Executor)
  }

  def "#createConnection creates a connection with the current settings"() {
    expect: with(factory.createConnection("id", "host", 80, listener, timeoutController, executor)) { connection ->
      connection != null

      connection.id == "id"
      connection.host == "host"
      connection.port == 80
      connection.listener == listener
      connection.timeoutController == timeoutController
      connection.executor == executor

      connection.disconnectIfNonKeepAliveRequest == factory.disconnectIfNonKeepAliveRequest
      connection.allowNonIdempotentPipelining == factory.allowNonIdempotentPipelining
      connection.maxRequestsInPipeline == factory.maxRequestsInPipeline
    }
  }
}
