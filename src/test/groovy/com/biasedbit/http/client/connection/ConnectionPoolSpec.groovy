package com.biasedbit.http.client.connection

import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class ConnectionPoolSpec extends Specification {

  ConnectionPool pool
  HttpConnection connection

  def setup() {
    pool = new ConnectionPool()
    connection = Mock(HttpConnection)
  }

  def "#connectionOpening increments the number of connections"() {
    when: pool.connectionOpening()
    then: pool.totalConnections() == 1

    when: pool.connectionOpening()
    then: pool.totalConnections() == 2
  }

  def "#connectionFailed decreases the number of connections and sets the connection failure flag"() {
    setup:
    pool.connectionOpening()
    pool.connectionOpening()

    when: pool.connectionFailed()
    then: pool.totalConnections() == 1
    and: pool.hasConnectionFailures()
  }

  def "#connectionOpen increments the number of connections"() {
    setup: assert pool.totalConnections() == 0
    when: pool.connectionOpen(connection)
    then: pool.totalConnections() == 1
  }

  def "#connectionOpen does not increment the number of connections if there were connections opening"() {
    setup: pool.connectionOpening()
    when: pool.connectionOpen(connection)
    then: pool.totalConnections() == 1
  }

  def "#connectionOpen clears the connection failure flag"() {
    setup:
    pool.connectionOpening()
    pool.connectionFailed()
    pool.connectionOpening()

    when: pool.connectionOpen(connection)
    then: !pool.hasConnectionFailures()
    and: pool.totalConnections() == 1
  }
}
