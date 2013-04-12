package com.biasedbit.http.client.future

import com.biasedbit.http.client.connection.HttpConnection
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import spock.lang.Specification

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultHttpFutureSpec extends Specification {

  def future = new DefaultHttpRequestFuture()
  def response = new DefaultHttpResponse(HTTP_1_1, OK)

  // mutable request future

  def "#finishedSuccessfully triggers successful completion of the future"() {
    given: "a future which has been started"
    future.markExecutionStart()

    and: "the future has a couple of listeners"
    def listeners = [createListener(), createListener()]
    listeners.each { future.addListener(it) }

    expect: "it to accept being finished"
    future.finishedSuccessfully("finished", response)

    and: "it to be marked as completed"
    with(future) {
      it.isDone()
      it.isSuccessful()
      it.isSuccessfulResponse()
      it.processedResult == "finished"
      it.response == response
      it.status == response.status
      it.responseStatusCode == response.status.code
      it.cause == null
      !it.cancelled
    }

    and: "all the listeners to have been notified"
    listeners.each { assert it.notified }
  }

  def "#failedWithCause triggers completion of the future with an exception as cause"() {
    given: "a future which has been started"
    future.markExecutionStart()

    and: "the future has a couple of listeners"
    def listeners = [createListener(), createListener()]
    listeners.each { future.addListener(it) }

    expect: "it to accept being finished"
    future.failedWithCause(HttpRequestFuture.EXECUTION_REJECTED)

    and: "it to be marked as completed"
    with(future) {
      it.isDone()
      !it.isSuccessful()
      !it.isSuccessfulResponse()
      it.processedResult == null
      it.response == null
      it.status == null
      it.responseStatusCode == -1
      it.cause == HttpRequestFuture.EXECUTION_REJECTED
      !it.cancelled
    }

    and: "all the listeners to have been notified"
    listeners.each { assert it.notified }
  }

  def "#failedWithCause triggers completion of the future with an exception as cause and the received response"() {
    given: "a future which has been started"
    future.markExecutionStart()

    and: "the future has a couple of listeners"
    def listeners = [createListener(), createListener()]
    listeners.each { future.addListener(it) }

    expect: "it to accept being finished"
    future.failedWithCause(HttpRequestFuture.EXECUTION_REJECTED, response)

    and: "it to be marked as completed"
    with(future) {
      it.isDone()
      !it.isSuccessful()
      it.isSuccessfulResponse()
      it.processedResult == null
      it.response == response
      it.status == response.status
      it.responseStatusCode == response.status.code
      it.cause == HttpRequestFuture.EXECUTION_REJECTED
      !it.cancelled
    }

    and: "all the listeners to have been notified"
    listeners.each { assert it.notified }
  }

  // read-only request future

  def "#getExecutionTime returns -1 when the future hasn't been started"() {
    expect: future.executionTime == -1
  }

  def "#getExecutionTime returns -1 when the future hasn't been finished"() {
    given: future.markExecutionStart()
    expect: future.executionTime == -1
  }

  def "#getExecutionTime returns N > 0 when the future is done"() {
    given: future.markExecutionStart()
    and: sleep(100)
    and: future.finishedSuccessfully("finished", new DefaultHttpResponse(HTTP_1_1, OK))
    expect: future.executionTime > 0
  }

  def "#getExistenceTime returns the time elapsed since creation if the future hasn't been finished"() {
    given: sleep(20)
    expect: future.existenceTime >= 20
  }

  def "#getExistenceTime returns the time elapsed since creation and completion"() {
    given: sleep(100)
    and: future.finishedSuccessfully("finished", response)
    and: sleep(1000)
    expect: with(future.existenceTime) {
      it >= 100
      it < 300 // give it some leeway, test conditions vary; we're just testing that it falls closer to 100ms than 1s
    }
  }

  def "#addListener immediately notifies the listener when one is added after completion"() {
    given: "a completed future"
    future.markExecutionStart()
    future.finishedSuccessfully("result", response)

    and: "a listener"
    def listener = createListener()

    when: "the listener is attached to the future"
    future.addListener(listener)

    then: "it is immediately notified of completion"
    listener.notified
  }

  def "#removeListener removes a listener"() {
    given: "a listener attached to the future"
    def listener = createListener()
    future.addListener(listener)

    when: "the listener is removed prior to the future being finished"
    future.removeListener(listener)

    and: "the future is finished"
    future.finishedSuccessfully("result", response)

    then: "the listener is not notified"
    !listener.notified
  }

  def "#cancelled triggers completion of the future with an exception as cause and the received response"() {
    given: "a future which has been started and has an attached connection"
    future.markExecutionStart()
    def connection = Mock(HttpConnection) { 1 * terminate(HttpRequestFuture.CANCELLED) }
    future.attachConnection(connection)

    and: "the future has a couple of listeners"
    def listeners = [createListener(), createListener()]
    listeners.each { future.addListener(it) }

    expect: "it to accept being cancelled"
    future.cancel()

    and: "it to be marked as completed"
    with(future) {
      it.isDone()
      !it.isSuccessful()
      !it.isSuccessfulResponse()
      it.processedResult == null
      it.response == null
      it.status == null
      it.responseStatusCode == -1
      it.cause == HttpRequestFuture.CANCELLED
      it.cancelled
    }

    and: "all the listeners to have been notified"
    listeners.each { assert it.notified }
  }

  def "it gracefully handles exceptions when notifiying listeners"() {
    given: "an attached listener which raises exception when notified"
    def misbehavingListener = new HttpRequestFutureListener() {
      @Override void operationComplete(HttpRequestFuture future) throws Exception { throw new Exception("kaboom") }
    }
    future.addListener(misbehavingListener)

    when: "the listener is notified of the future completion"
    future.finishedSuccessfully("finished", response)

    then: "no exception surfaces"
    noExceptionThrown()
  }

  def "#toString prints a descriptive output for each different state"() {
    given: "the string representation of an unfinished future"
    def inProgress = future.toString()

    and: "the string representation of a successfully finished future"
    future.finishedSuccessfully("finished", response)
    def success = future.toString()

    and: "the string representation of failed future"
    def failedFuture = new DefaultHttpRequestFuture()
    failedFuture.failedWithCause(HttpRequestFuture.CANCELLED)
    def failure = failedFuture.toString()

    expect: "them all to be different"
    inProgress != success
    inProgress != failure
    success != failure
  }

  private def createListener() {
    return new HttpRequestFutureListener() {
      def notified = false
      @Override void operationComplete(HttpRequestFuture future) throws Exception { notified = true }
    }
  }
}
