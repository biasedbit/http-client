package com.biasedbit.http.client

import com.biasedbit.http.client.connection.Connection
import com.biasedbit.http.client.connection.ConnectionFactory
import com.biasedbit.http.client.connection.DefaultConnection
import com.biasedbit.http.client.event.EventType
import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.server.DummyHttpServer
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpHeaders
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static com.biasedbit.http.client.future.RequestFuture.*
import static org.jboss.netty.handler.codec.http.HttpMethod.GET
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpClientTest extends Specification {

  def host    = "localhost"
  def port    = 8081
  def client  = new DefaultHttpClient()
  def request = new DefaultHttpRequest(HTTP_1_1, GET, "/")
  def server  = new DummyHttpServer(host, port)

  def setup() {
    assert server.init()

    client.connectionTimeout = 500
    client.maxQueuedRequests = 50
    assert client.init()
    assert client.initialized
  }

  public void cleanup() {
    server.terminate()
    client.terminate()
  }

  @Timeout(3)
  def "it fails requests with TIMED_OUT cause if server doesn't respond within the configured request timeout"() {
    setup: server.responseLatency = 1000
    when: def future = client.execute(host, port, 100, request, new DiscardProcessor())
    then: future.await(2, TimeUnit.SECONDS)
    and: future.isDone()
    and: !future.isSuccessful()
    and: future.getCause() == TIMED_OUT
  }

  @Timeout(2)
  def "it fails with CANNOT_CONNECT if connection fails"() {
    setup: server.terminate()
    when: def future = client.execute(host, port, request, new DiscardProcessor())
    then: future.await(1, TimeUnit.SECONDS)
    and: future.isDone()
    and: !future.isSuccessful()
    and: future.getCause() == CANNOT_CONNECT
  }

  @Timeout(2)
  def "it connects with SSL"() {
    given: "a server that accepts SSL connections"
    server.terminate()
    server.useSsl = true
    assert server.init()

    and: "a client that is configured to use SSL"
    client = new DefaultHttpClient()
    client.useSsl = true
    assert client.init()

    expect: "it to successfully execute its request"
    with(client.execute(host, port, request)) { future ->
      future.await(1, TimeUnit.SECONDS)
      future.isDone()
      future.isSuccessful()
    }
  }

  @Timeout(2)
  def "it supports NIO mode"() {
    given: "a client configured to use NIO"
    client = new DefaultHttpClient()
    client.useNio = true
    assert client.init()

    expect: "it to successfully execute"
    with(client.execute(host, port, request)) { future ->
      future.await(1, TimeUnit.SECONDS)
      future.isDone()
      future.isSuccessful()
    }
  }

  def "it cancels all pending request when a premature shutdown is issued"() {
    setup: "the server takes 50ms to process each response"
    server.responseLatency = 50

    and: "50 requests are executed"
    def request = new DefaultHttpRequest(HTTP_1_1, GET, "/")
    def futures = []
    50.times { futures << client.execute("localhost", 8081, request) }

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

  def "it opens up new connections if they are closed between requests to the same host/port"() {
    given: "a stats gathering client"
    def client = new StatsGatheringHttpClient()
    assert client.init()

    and: "and two requests that will trigger a connection close"
    request.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
    def secondRequest = new DefaultHttpRequest(HTTP_1_1, GET, "/")
    secondRequest.addHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)

    when: "it executes both requests to completion"
    client.execute(host, port, request)
    client.execute(host, port, secondRequest).awaitUninterruptibly()

    then: "it will have two connection closed events"
    client.getProcessedEvents(EventType.CONNECTION_CLOSED) == 2
  }


  def "it adds an 'Accept-Encoding' header set to 'gzip' when auto decompression is enabled"() {
    setup:
    def latch = new CountDownLatch(1)

    client = new DefaultHttpClient()
    client.autoDecompress = true
    Connection connection = null
    // This is kind of hard to test, given all the stuff that goes on...
    // This test has *a lot* of implementation knowledge, which is a natural code smell but re-structuring the whole
    // thing just to make it more testable on such a minor feature seems kind of overkill.
    client.connectionFactory = Mock(ConnectionFactory) {
      createConnection(_, _, _, _, _, _) >> { args ->
        connection = Spy(DefaultConnection, constructorArgs: args)
        connection.execute(_) >> { a ->
          assert a[0].request == request
          assert a[0].request.getHeader(HttpHeaders.Names.ACCEPT_ENCODING) == HttpHeaders.Values.GZIP
          latch.countDown()
          true
        }
        connection
      }
    }
    assert client.init()

    when: client.execute(host, port, request)
    then: latch.await(1, TimeUnit.SECONDS)
  }
}
