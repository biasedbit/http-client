package com.biasedbit.http.client.processor

import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.util.CharsetUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.valueOf
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class AbstractAccumulatorProcessorSpec extends Specification {

  def processor = new AbstractAccumulatorProcessor<ChannelBuffer>() {
    @Override protected ChannelBuffer convertBufferToResult(ChannelBuffer buffer) { buffer }
  }

  def buffer = copiedBuffer("biasedbit", CharsetUtil.UTF_8)

  def "it immediately processes content if request has content and is not chunked"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    HttpHeaders.setContentLength(response, buffer.readableBytes())
    response.content = buffer

    expect: processor.willProcessResponse(response)
    and: buffer.equals(processor.processedResponse)
  }

  @Unroll
  def "it rejects #outcome response with code #code when configured to accept response code(s) #acceptable"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, valueOf(code))
    HttpHeaders.setContentLength(response, buffer.readableBytes())
    response.chunked = true
    processor = new AbstractAccumulatorProcessor<ChannelBuffer>(acceptable) {
      @Override protected ChannelBuffer convertBufferToResult(ChannelBuffer buffer) { buffer }
    }
    expect: processor.willProcessResponse(response) == expect
    where:
    acceptable | code | outcome   | expect
    [200, 201] | 300  | "rejects" | false
    200        | 300  | "rejects" | false
    [200, 201] | 201  | "accepts" | true
    200        | 200  | "accepts" | true
  }

  def "it rejects chunked responses without content"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    HttpHeaders.setContentLength(response, 0)
    response.chunked = true

    expect: !processor.willProcessResponse(response)
  }

  def "it rejects non-chunked responses without content"() {
    setup: def response = new DefaultHttpResponse(HTTP_1_1, OK)
    expect: !processor.willProcessResponse(response)
  }

  def "it rejects chunked responses with length over Integer.MAX_VALUE (interpreted as negative value)"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    HttpHeaders.setContentLength(response, Integer.MAX_VALUE + 1)
    response.chunked = true

    expect: !processor.willProcessResponse(response)
  }

  def "it returns null when no data is added"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    HttpHeaders.setContentLength(response, buffer.readableBytes())
    response.chunked = true
    assert processor.willProcessResponse(response)

    expect: processor.processedResponse == null
  }

  def "it returns null when data is added but #addLastData is not called"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    HttpHeaders.setContentLength(response, buffer.readableBytes())
    response.chunked = true
    assert processor.willProcessResponse(response)

    when: processor.addData(copiedBuffer("biased", CharsetUtil.UTF_8))
    then: processor.processedResponse == null
  }

  def "it returns the accumulated data"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    HttpHeaders.setContentLength(response, buffer.readableBytes())
    response.chunked = true
    assert processor.willProcessResponse(response)
    processor.addData(copiedBuffer("biased", CharsetUtil.UTF_8))
    processor.addLastData(copiedBuffer("bit", CharsetUtil.UTF_8))

    expect: buffer.equals(processor.processedResponse)
  }

  def "it returns the accumulated data even if the request has no length indication"() {
    setup:
    def response = new DefaultHttpResponse(HTTP_1_1, OK)
    response.chunked = true
    assert processor.willProcessResponse(response)
    processor.addData(copiedBuffer("biased", CharsetUtil.UTF_8))
    processor.addLastData(copiedBuffer("bit", CharsetUtil.UTF_8))

    expect: buffer.equals(processor.processedResponse)
  }
}
