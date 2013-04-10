package com.biasedbit.http.client.host

import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHostContextFactorySpec extends Specification {

  HostContextFactory factory

  def setup() { factory = new DefaultHostContextFactory() }

  def "#createHostContext creates a host context with input parameters"() {
    expect: with(factory.createHostContext("host", 80, 10)) { context ->
      context instanceof DefaultHostContext

      context.host == "host"
      context.port == 80
      context.maxConnections == 10
    }
  }
}
