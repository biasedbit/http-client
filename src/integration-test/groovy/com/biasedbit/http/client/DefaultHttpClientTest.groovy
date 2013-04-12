package com.biasedbit.http.client

import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.server.DummyHttpServer
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpRequest
import spock.lang.Specification
import spock.lang.Unroll

import static com.biasedbit.http.client.future.HttpRequestFuture.CANNOT_CONNECT
import static com.biasedbit.http.client.future.HttpRequestFuture.SHUTTING_DOWN
import static com.biasedbit.http.client.future.HttpRequestFuture.TIMED_OUT
import static org.jboss.netty.handler.codec.http.HttpMethod.GET
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpClientTest extends Specification {

  String            host
  int               port
  DummyHttpServer   server
  DefaultHttpClient client
  HttpRequest       request

  def setup() {
    host = "localhost"
    port = 8081
    server = new DummyHttpServer(host, port)
    assert server.init()

    client = new DefaultHttpClient()
    client.connectionTimeout = 500
    client.maxQueuedRequests = 50
    assert client.init()

    request = new DefaultHttpRequest(HTTP_1_1, GET, "/")
  }

  public void cleanup() {
    server.terminate()
    client.terminate()
  }

  def "it fails requests with TIMED_OUT cause if server doesn't respond within the configured request timeout"() {
    setup: server.responseLatency = 1000
    when: def future = client.execute(host, port, 100, request, new DiscardProcessor())
    then: future.awaitUninterruptibly(5000)
    and: future.isDone()
    and: !future.isSuccessful()
    and: future.getCause() == TIMED_OUT
  }

  def "it fails with CANNOT_CONNECT if connection fails"() {
    setup: server.terminate()
    when: def future = client.execute(host, port, 100, request, new DiscardProcessor())
    then: future.awaitUninterruptibly(1000)
    and: future.isDone()
    and: !future.isSuccessful()
    and: future.getCause() == CANNOT_CONNECT
  }

  def "it raises exception when requests queue limit overflows"() {
    setup:
    server.terminate()
    client.maxQueuedRequests.times { client.execute(host, port, 100, request, new DiscardProcessor()) }

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

  def "it cancels all pending request when a premature shutdown is issued"() {
    setup: "the server takes 50ms to process each response"
    server.responseLatency = 50

    and: "50 requests are executed"
    def request = new DefaultHttpRequest(HTTP_1_1, GET, "/")
    def futures = []
    50.times { futures << client.execute("localhost", 8081, request, new DiscardProcessor()) }

    when: "the client is terminated 150ms after the requests are sent"
    sleep(150)
    client.terminate()

    then: "all requests will be done"
    futures.each { assert it.isDone() }

    and: "the first 3+ requests will have finished successfully and the others will be terminated with SHUTTING_DOWN"
    long complete = 0
    futures.each {
      if (it.isSuccessful()) complete++
      else assert it.cause == SHUTTING_DOWN
    }

    // Server is configured to sleep for 50ms in each request so only the first 3 should complete, although when
    // running the tests in different environments (IDE, command line, etc) results may actually vary a bit
    complete >= 3
    complete <= 20
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
    "requestCompressionLevel"     | 3
    "autoInflate"                 | false
    "cleanupInactiveHostContexts" | true
    "connectionFactory"           | null
    "hostContextFactory"          | null
    "futureFactory"               | null
    "timeoutController"           | null
    "sslContextFactory"           | null
  }
}
