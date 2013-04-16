package com.biasedbit.http.client.util

import com.biasedbit.http.client.connection.Connection
import com.biasedbit.http.client.processor.ResponseProcessor
import org.jboss.netty.handler.codec.http.HttpRequest
import spock.lang.Specification

import static com.biasedbit.http.client.util.HostController.DrainQueueResult.*

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class HostControllerSpec extends Specification {

  def pool = Stub(ConnectionPool, constructorArgs: [3])
  def context = new HostController("biasedbit.com", 80, pool)

  def "-isCleanable returns true when the pool has no connections and the request queue is empty"() {
    expect: context.cleanable
  }

  def "-isCleanable returns false when there are queued requests"() {
    setup: context.addToQueue(createRequestContext())
    expect: !context.cleanable
  }

  def "-isCleanable returns false when the pool has connections"() {
    setup: pool.hasConnections() >> true
    expect: !context.cleanable
  }

  def "-addToQueue raises exception if HttpRequestContext host doesn't match"() {
    setup:
    def requestContext = createRequestContext("github.com")
    when: context.addToQueue(requestContext)
    then: thrown(IllegalArgumentException)
  }

  def "-addToQueue raises exception if HttpRequestContext port doesn't match"() {
    setup: def requestContext = createRequestContext("biasedbit.com", 81)
    when: context.addToQueue(requestContext)
    then: thrown(IllegalArgumentException)
  }

  def "-restoreRequestsToQueue adds multiple requests to the queue"() {
    setup:
    def requests = [createRequestContext(), createRequestContext()]
    def connection = Mock(Connection) {
      isAvailable() >> true
      1 * execute(requests[0]) >> true // execute() must be called twice with both requests in order
      1 * execute(requests[1]) >> true
    }
    pool.hasConnections() >> true
    pool.connections >> [connection]

    when: context.restoreRequestsToQueue(requests)
    then: context.drainQueue() == DRAINED
  }

  def "-drainQueue returns QUEUE_EMPTY if the queue has no requests"() {
    expect: context.drainQueue() == QUEUE_EMPTY
  }

  def "-drainQueue returns OPEN_CONNECTION if connection pool has no established connections but has room for more"() {
    given: context.addToQueue(createRequestContext())
    and: pool.hasConnections() >> false
    and: pool.hasAvailableSlots() >> true
    expect: context.drainQueue() == OPEN_CONNECTION
  }

  def "-drainQueue returns NOT_DRAINED if connection pool has no established connections nor room for more"() {
    given: context.addToQueue(createRequestContext())
    and: pool.hasConnections() >> false
    and: pool.hasAvailableSlots() >> false
    expect: context.drainQueue() == NOT_DRAINED
  }

  def "-drainQueue returns DRAINED if an available connection accepts a request"() {
    given: def request = createRequestContext()
    and: context.addToQueue(request)
    and: pool.hasConnections() >> true
    and: pool.getConnections() >> [Mock(Connection) {
      isAvailable() >> true
      1 * execute(request) >> true
    }]
    expect: context.drainQueue() == DRAINED
  }

  def "-drainQueue returns OPEN_CONNECTION when no established connections are available but pool has room for more"() {
    given: context.addToQueue(createRequestContext())
    and: pool.hasConnections() >> true
    and: pool.getConnections() >> [Mock(Connection) { isAvailable() >> false }]
    and: pool.hasAvailableSlots() >> true
    expect: context.drainQueue() == OPEN_CONNECTION
  }

  def "-drainQueue returns NOT_DRAINED when no established connections are available nor pool has room for more"() {
    given: context.addToQueue(createRequestContext())
    and: pool.hasConnections() >> true
    and: pool.getConnections() >> [Mock(Connection) { isAvailable() >> false }]
    and: pool.hasAvailableSlots() >> false
    expect: context.drainQueue() == NOT_DRAINED
  }

  private RequestContext createRequestContext(String host = "biasedbit.com", int port = 80) {
    new RequestContext(host, port, 100, Mock(HttpRequest), Mock(ResponseProcessor))
  }
}
