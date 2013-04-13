package com.biasedbit.http.client

import com.biasedbit.http.client.processor.DiscardProcessor
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpClientSpec extends Specification {

  def host    = "localhost"
  def port    = 60000
  def client  = new DefaultHttpClient()
  def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")

  def setup() {
    client.maxQueuedRequests = 5
    assert client.init()
  }

  public void cleanup() { client.terminate() }

  def "it raises exception when requests queue limit overflows"() {
    setup: client.maxQueuedRequests.times { client.execute(host, port, 100, request, new DiscardProcessor()) }
    when: client.execute(host, port, 100, request, new DiscardProcessor())
    then: thrown(CannotExecuteRequestException)
  }

  def "it raises exception when trying to execute a request without initializing the client"() {
    setup: client = new DefaultHttpClient()
    when: client.execute(host, port, 100, request, new DiscardProcessor())
    then: thrown(CannotExecuteRequestException)
  }

  def "it raises exception when trying to execute a request after the client has been terminated"() {
    setup: client.terminate()
    when: client.execute(host, port, 100, request, new DiscardProcessor())
    then: thrown(CannotExecuteRequestException)
  }

  @Unroll
  def "it doesn't allow changing the '#property' property after it has been initialized"() {
    when: client."${property}" = value
    then: thrown(IllegalStateException)
    where:
    property | value
    "connectionTimeout"           | 1000
    "requestInactivityTimeout"    | 1000
    "useNio"                      | false
    "useSsl"                      | false
    "maxConnectionsPerHost"       | 2
    "maxQueuedRequests"           | 2
    "maxIoWorkerThreads"          | 3
    "maxHelperThreads"            | 3
    "autoDecompress"              | false
    "cleanupInactiveHostContexts" | true
    "connectionFactory"           | null
    "hostContextFactory"          | null
    "futureFactory"               | null
    "timeoutController"           | null
    "sslContextFactory"           | null
  }
}
