package com.biasedbit.http.client

import com.biasedbit.http.client.future.HttpDataSinkListener
import com.biasedbit.http.client.processor.DiscardProcessor
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import spock.lang.Specification
import spock.lang.Unroll

import static org.jboss.netty.handler.codec.http.HttpMethod.*
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpClientSpec extends Specification {

  def host    = "localhost"
  def port    = 60000
  def client  = new DefaultHttpClient()
  def request = new DefaultHttpRequest(HTTP_1_1, GET, "/")

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
  def "#execute accepts a '#method' request with an HttpDataSinkListener"() {
    given: request = new DefaultHttpRequest(HTTP_1_1, method, "/")
    when: client.execute(host, port, 100, request, new DiscardProcessor(), Mock(HttpDataSinkListener))
    then: noExceptionThrown()

    where:
    execute = "#execute"
    method << [POST, PUT, PATCH]
  }

  @Unroll
  def "#execute raises exception if a '#method' request is submitted with an HttpDataSinkListener"() {
    given: request = new DefaultHttpRequest(HTTP_1_1, method, "/")
    when: client.execute(host, port, 100, request, new DiscardProcessor(), Mock(HttpDataSinkListener))
    then: thrown(IllegalArgumentException)

    where:
    execute = "#execute"
    method << [OPTIONS, GET, HEAD, DELETE, TRACE, CONNECT]
  }

//  def "#execute adds an 'Accept-Encoding' header set to 'gzip' when auto decompression is enabled"() {
//    setup:
//    client = new DefaultHttpClient()
//    client.autoDecompress = true
//    ???
//  }

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
    "futureFactory"               | null
    "timeoutController"           | null
    "sslContextFactory"           | null
  }
}
