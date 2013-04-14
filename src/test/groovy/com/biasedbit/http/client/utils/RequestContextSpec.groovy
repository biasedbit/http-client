package com.biasedbit.http.client.utils

import com.biasedbit.http.client.future.MutableRequestFuture
import com.biasedbit.http.client.processor.HttpResponseProcessor
import com.biasedbit.http.client.util.RequestContext
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll

import static org.jboss.netty.handler.codec.http.HttpMethod.*

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class RequestContextSpec extends Specification {

  HttpRequest           request
  HttpResponseProcessor processor
  MutableRequestFuture         future

  def setup() {
    request = Mock(HttpRequest)
    processor = Mock(HttpResponseProcessor)
    future = Mock(MutableRequestFuture)
  }

  def "it doesn't accept a null host"() {
    when: new RequestContext(null, 80, 500, request, processor, future)
    then: thrown(IllegalArgumentException)
  }

  def "it doesn't accept ports below 1 or above 65536"() {
    when: new RequestContext(null, port, 500, request, processor, future)
    then: thrown(IllegalArgumentException)
    where: port << [-1, 0, 65536, 67000]
  }

  def "it doesn't accept a null request"() {
    when: new RequestContext("biasedbit.com", 80, 500, null, processor, future)
    then: thrown(IllegalArgumentException)
  }

  def "it doesn't accept a null processor"() {
    when: new RequestContext("biasedbit.com", 80, 500, request, null, future)
    then: thrown(IllegalArgumentException)
  }

  def "it doesn't accept a null future"() {
    when: new RequestContext("biasedbit.com", 80, 500, request, processor, null)
    then: thrown(IllegalArgumentException)
  }

  def "if adjusts timeout to 0 (no timeout) if timeout parameter is invalid"() {
    when: def context = new RequestContext("biasedbit.com", 80, -1, request, processor, future)
    then: context.timeout == 0
  }

  @Unroll def "#isIdempotent returns #value when method is #method"() {
    setup: def context = new RequestContext("biasedbit.com", 80, 500, request, processor, future)
    and: request.method >> method

    expect: context.isIdempotent() == value

    where:
    method  | value
    OPTIONS | true
    GET     | true
    HEAD    | true
    POST    | false
    PUT     | true
    PATCH   | false
    DELETE  | true
    TRACE   | true
    CONNECT | false

    isIdempotent = "#isIdempotent"
  }

  def "#toString prints a nice description"() {
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, GET, "/index")
    def context = new RequestContext("biasedbit.com", 80, 500, request, processor, future)
    expect: context.toString().startsWith("GET /index (biasedbit.com:80)")
  }
}