package com.biasedbit.http.client.host

import com.biasedbit.http.client.connection.ConnectionPool
import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHostContextFactorySpec extends Specification {

  def factory = new DefaultHostContextFactory()
  def pool = new ConnectionPool(3)

  def "#createHostContext creates a host context with input parameters"() {
    expect: with(factory.createHostContext("host", 80, pool)) { context ->
      context != null

      context.host == "host"
      context.port == 80
      context.connectionPool == pool
    }
  }
}
