package com.biasedbit.http.client.processor

import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.util.CharsetUtil
import spock.lang.Specification

import static org.jboss.netty.buffer.ChannelBuffers.*
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_0
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import static org.jboss.netty.util.CharsetUtil.*

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class TypedDiscardProcessorSpec extends Specification {

  TypedDiscardProcessor<Object> processor

  def setup() { processor = new TypedDiscardProcessor<Object>() }

  def "#getInstance returns the singleton instance"() {
    setup: def instance = TypedDiscardProcessor.instance
    expect: instance == TypedDiscardProcessor.instance
  }

  def "#willProcessResponse always returns false"() {
    expect: !processor.willProcessResponse(response)
    where:
    response << [
        new DefaultHttpResponse(HTTP_1_0, OK),
        new DefaultHttpResponse(HTTP_1_1, OK),
        new DefaultHttpResponse(HTTP_1_1, TEMPORARY_REDIRECT),
        new DefaultHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR),
        null
    ]
  }

  def "#getProcessedResponse always returns null"() {
    expect: processor.processedResponse == null

    when: processor.addData(copiedBuffer("test", UTF_8))
    and: processor.addLastData(copiedBuffer("more test", UTF_8))
    then: processor.processedResponse == null
  }
}
