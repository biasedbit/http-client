package com.biasedbit.http.client

import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.server.DummyHttpServer
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import spock.lang.Specification

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
    with(client.execute(host, port, request, new DiscardProcessor())) { future ->
      future.awaitUninterruptibly()
      future.isDone()
      future.isSuccessful()
    }
  }

  def "it supports Old I/O mode"() {
    given: "a client configured to use OIO"
    client = new DefaultHttpClient()
    client.useNio = false
    assert client.init()

    expect: "it to successfully execute"
    with(client.execute(host, port, request, new DiscardProcessor())) { future ->
      future.awaitUninterruptibly(1000)
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
}
