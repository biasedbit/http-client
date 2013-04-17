package com.biasedbit.http.client.processor

import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpVersion
import org.jboss.netty.util.CharsetUtil
import spock.lang.Specification

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class ByteAccumulatorProcessorSpec extends Specification {

  def processor = new ByteAccumulatorProcessor()

  def setup() {
    def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    HttpHeaders.setContentLength(response, 9)
    response.setChunked(true)

    assert processor.willProcessResponse(response)
  }

  def "it converts the buffer contents to a byte[]"() {
    setup: processor.addData(copiedBuffer("biased", CharsetUtil.UTF_8))
    and: processor.addLastData(copiedBuffer("bit", CharsetUtil.UTF_8))
    expect: Arrays.equals(processor.processedResponse, "biasedbit".bytes)
  }

  def "it returns nothing if no content is received"() {
    expect: processor.processedResponse == null
  }
}
