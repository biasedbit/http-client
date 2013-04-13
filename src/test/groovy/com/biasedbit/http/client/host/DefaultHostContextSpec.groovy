package com.biasedbit.http.client.host

import com.biasedbit.http.client.connection.ConnectionPool
import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHostContextSpec extends Specification {

  def pool = Stub(ConnectionPool, constructorArgs: [3])

  def "it preserves the creation arguments"() { // doh
    expect: with(new DefaultHostContext("biasedbit.com", 80, pool)) {
      it.host == "biasedbit.com"
      it.port == 80
      it.connectionPool == pool
    }
  }
}
