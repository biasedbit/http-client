package com.biasedbit.http.client

import com.biasedbit.http.client.future.HttpRequestFuture
import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.server.DummyHttpServer
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpVersion
import org.junit.After
import spock.lang.Specification

import static org.jboss.netty.handler.codec.http.HttpMethod.*
import static org.jboss.netty.handler.codec.http.HttpVersion.*

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpClientTest extends Specification {

  String          host
  int             port
  DummyHttpServer server
  HttpClient      client
  HttpRequest     request

  def setup() {
    host = "localhost"
    port = 8081
    server = new DummyHttpServer(host, port)
    assert server.init()

    client = new DefaultHttpClient()
    assert client.init()

    request = new DefaultHttpRequest(HTTP_1_1, GET, "/");
  }

  public void cleanup() {
    server.terminate()
    client.terminate()
  }

  def "it times out if server doesn't respond within the configured request timeout"() {
    setup: server.responseLatency = 1000
    when: def future = client.execute(host, port, 100, request, new DiscardProcessor());
    then: future.awaitUninterruptibly(5000)
    and: future.isDone()
    and: !future.isSuccess()
    and: future.getCause() == HttpRequestFuture.TIMED_OUT
  }
}
