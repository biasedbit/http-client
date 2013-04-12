package com.biasedbit.http.client

import com.biasedbit.http.client.connection.HttpConnectionFactory
import com.biasedbit.http.client.future.HttpRequestFutureFactory
import com.biasedbit.http.client.host.HostContextFactory
import com.biasedbit.http.client.ssl.SslContextFactory
import com.biasedbit.http.client.timeout.TimeoutController
import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpClientFactorySpec extends Specification {

  DefaultHttpClientFactory factory = new DefaultHttpClientFactory()

  def setup() {
    factory.hostContextFactory = Mock(HostContextFactory)
    factory.connectionFactory = Mock(HttpConnectionFactory)
    factory.futureFactory = Mock(HttpRequestFutureFactory)
    factory.timeoutController = Mock(TimeoutController)
    factory.sslContextFactory = Mock(SslContextFactory)
  }

  def "#createClient creates a host context with input parameters"() {
    expect: with(factory.createClient()) { client ->
      client != null

      client.connectionTimeout == factory.connectionTimeout
      client.requestInactivityTimeout == factory.requestInactivityTimeout
      client.useSsl == factory.useSsl
      client.useNio == factory.useNio
      client.autoDecompress == factory.autoDecompress
      client.maxConnectionsPerHost == factory.maxConnectionsPerHost
      client.maxQueuedRequests == factory.maxQueuedRequests
      client.maxIoWorkerThreads == factory.maxIoWorkerThreads
      client.maxHelperThreads == factory.maxHelperThreads
      client.cleanupInactiveHostContexts == factory.cleanupInactiveHostContexts

      client.hostContextFactory == factory.hostContextFactory
      client.connectionFactory == factory.connectionFactory
      client.futureFactory == factory.futureFactory
      client.timeoutController == factory.timeoutController
      client.sslContextFactory == factory.sslContextFactory
    }
  }
}
