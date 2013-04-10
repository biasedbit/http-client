package com.biasedbit.http.client.future

import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpRequestFutureFactorySpec extends Specification {

  HttpRequestFutureFactory factory

  def setup() { factory = new DefaultHttpRequestFutureFactory() }

  def "#createFuture creates a default future"() {
    expect: with(factory.createFuture()) { it != null }
  }
}
