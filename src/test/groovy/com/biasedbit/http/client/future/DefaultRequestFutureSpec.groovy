package com.biasedbit.http.client.future

import com.biasedbit.http.client.connection.Connection
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class DefaultRequestFutureSpec extends Specification {

  def future   = new DefaultRequestFuture()
  def response = new DefaultHttpResponse(HTTP_1_1, OK)

  // mutable request future

  def "-finishedSuccessfully triggers successful completion of the future"() {
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
      it.hasSuccessfulResponse()
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

  def "-failedWithCause triggers completion of the future with an exception as cause"() {
    given: "a future which has been started"
    future.markExecutionStart()

    and: "the future has a couple of listeners"
    def listeners = [createListener(), createListener()]
    listeners.each { future.addListener(it) }

    expect: "it to accept being finished"
    future.failedWithCause(RequestFuture.EXECUTION_REJECTED)

    and: "it to be marked as completed"
    with(future) {
      it.isDone()
      !it.isSuccessful()
      !it.hasSuccessfulResponse()
      it.processedResult == null
      it.response == null
      it.status == null
      it.responseStatusCode == -1
      it.cause == RequestFuture.EXECUTION_REJECTED
      !it.cancelled
    }

    and: "all the listeners to have been notified"
    listeners.each { assert it.notified }
  }

  def "-failedWithCause triggers completion of the future and terminates the connection if it is still available"() {
    given: "a future which has been started"
    future.markExecutionStart()

    and: "it has been attached to a busy connection"
    def connection = Mock(Connection) { isAvailable() >> false }
    future.attachConnection(connection)

    when: "a timeout failure is triggered"
    future.failedWithCause(RequestFuture.TIMED_OUT)

    then: "the future will be marked as complete"
    with(future) {
      it.isDone()
      !it.isSuccessful()
      it.cause == RequestFuture.TIMED_OUT
      !it.cancelled
    }

    and: "the connection will have been terminated"
    1 * connection.terminate(RequestFuture.TIMED_OUT)
  }

  def "-failedWhileProcessingResponse triggers completion of the future with an exception and the received response"() {
    given: "a future which has been started"
    future.markExecutionStart()

    and: "the future has a couple of listeners"
    def listeners = [createListener(), createListener()]
    listeners.each { future.addListener(it) }

    expect: "it to accept being finished"
    future.failedWhileProcessingResponse(RequestFuture.EXECUTION_REJECTED, response)

    and: "it to be marked as completed"
    with(future) {
      it.isDone()
      !it.isSuccessful()
      it.hasSuccessfulResponse()
      it.processedResult == null
      it.response == response
      it.status == response.status
      it.responseStatusCode == response.status.code
      it.cause == RequestFuture.EXECUTION_REJECTED
      !it.cancelled
    }

    and: "all the listeners to have been notified"
    listeners.each { assert it.notified }
  }

  // read-only request future

  def "-getExecutionTime returns -1 when the future hasn't been started"() {
    expect: future.executionTime == -1
  }

  def "-getExecutionTime returns -1 when the future hasn't been finished"() {
    given: future.markExecutionStart()
    expect: future.executionTime == -1
  }

  def "-getExecutionTime returns N > 0 when the future is done"() {
    given: future.markExecutionStart()
    and: sleep(100)
    and: future.finishedSuccessfully("finished", new DefaultHttpResponse(HTTP_1_1, OK))
    expect: future.executionTime > 0
  }

  def "-getExistenceTime returns the time elapsed since creation if the future hasn't been finished"() {
    given: sleep(20)
    expect: future.existenceTime >= 20
  }

  def "-getExistenceTime returns the time elapsed since creation and completion"() {
    given: sleep(100)
    and: future.finishedSuccessfully("finished", response)
    and: sleep(1000)
    expect: with(future.existenceTime) {
      it >= 100
      it < 300 // give it some leeway, test conditions vary; we're just testing that it falls closer to 100ms than 1s
    }
  }

  def "-addListener immediately notifies the listener when one is added after completion"() {
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

  def "-removeListener removes a listener"() {
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

  def "-hasSuccessfulResponse only returns true if the status code of the response is in the range 200-299"() {
    setup: response = new DefaultHttpResponse(HTTP_1_1, status)
    when: future.finishedSuccessfully("finished", response)
    then: future.hasSuccessfulResponse() == successfulResponse
    where:
    status                     | successfulResponse
    PROCESSING /* 102 */       | false
    OK /* 200 */               | true
    CREATED /* 201*/           | true
    MULTIPLE_CHOICES /* 300 */ | false
  }

  def "-cancelled triggers completion of the future with an exception as cause and the received response"() {
    given: "a future which has been started and has an attached connection"
    future.markExecutionStart()
    def connection = Mock(Connection) { 1 * terminate(RequestFuture.CANCELLED) }
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
      !it.hasSuccessfulResponse()
      it.processedResult == null
      it.response == null
      it.status == null
      it.responseStatusCode == -1
      it.cause == RequestFuture.CANCELLED
      it.cancelled
    }

    and: "all the listeners to have been notified"
    listeners.each { assert it.notified }
  }

  @Timeout(1)
  def "-await() unlocks only then the future completes"() {
    given: scheduledEvent(100) { future.finishedSuccessfully("result", response) }
    expect: future.await() != null
  }

  @Timeout(1)
  def "-await() immediately unlocks if the future is already complete"() {
    given: future.finishedSuccessfully("result", response)
    expect: future.await() != null
  }

  @Timeout(1)
  def "-await() raises InterruptedException if a thread interrupt occurs while waiting"() {
    given: scheduledInterruptForCurrentThread(100)
    when: future.await()
    then: thrown(InterruptedException)
  }

  @Timeout(1)
  def "-await(timeout, unit) unlocks as soon as the future completes"() {
    given: scheduledEvent(50) { future.finishedSuccessfully("result", response) }
    expect: future.await(100, TimeUnit.MILLISECONDS)
  }

  @Timeout(1)
  def "-await(timeout, unit) immediately returns if future is already complete"() {
    given: future.finishedSuccessfully("result", response)
    expect: future.await(1, TimeUnit.SECONDS)
  }

  @Timeout(1)
  def "-await(timeout, unit) unlocks after the specified timeout returning 'false' if the future is not complete"() {
    expect: !future.await(50, TimeUnit.MILLISECONDS)
  }

  @Timeout(1)
  def "-await(timeout, unit) raises InterruptedException if a thread interrupt occurs while waiting"() {
    given: scheduledInterruptForCurrentThread(100)
    when: future.await(1, TimeUnit.SECONDS)
    then: thrown(InterruptedException)
  }

  @Timeout(1)
  def "-await(timeout) unlocks as soon as the future completes"() {
    given: scheduledEvent(50) { future.finishedSuccessfully("result", response) }
    expect: future.await(100)
  }

  @Timeout(1)
  def "-await(timeout) immediately returns if future is already complete"() {
    given: future.finishedSuccessfully("result", response)
    expect: future.await(1000)
  }

  @Timeout(1)
  def "-await(timeout) unlocks after the specified timeout (in ms) returning 'false' if the future is not complete"() {
    expect: !future.await(50)
  }

  @Timeout(1)
  def "-await(timeout) raises InterruptedException if a thread interrupt occurs while waiting"() {
    given: scheduledInterruptForCurrentThread(100)
    when: future.await(1000)
    then: thrown(InterruptedException)
  }

  @Timeout(1)
  def "-awaitUninterruptibly() unlocks only then the future completes even if a thread interrupt occurs"() {
    given: scheduledEvent(100) { future.finishedSuccessfully("result", response) }
    and: scheduledInterruptForCurrentThread(50)
    expect: future.awaitUninterruptibly() != null
  }

  @Timeout(1)
  def "-awaitUninterruptibly() immediately returns if the future is already complete"() {
    given: future.finishedSuccessfully("result", response)
    expect: future.awaitUninterruptibly() != null
  }

  @Timeout(1)
  def "-awaitUninterruptibly(timeout, unit) unlocks after the specified timeout even if an interrupt occurs"() {
    given: scheduledEvent(100) { future.finishedSuccessfully("result", response) }
    and: scheduledInterruptForCurrentThread(50)
    expect: future.awaitUninterruptibly(200, TimeUnit.MILLISECONDS)
  }

  @Timeout(1)
  def "-awaitUninterruptibly(timeout, unit) immediately returns if the future is already complete"() {
    given: future.finishedSuccessfully("result", response)
    expect: future.awaitUninterruptibly()
  }

  @Timeout(1)
  def "-awaitUninterruptibly(timeout, unit) unlocks after timeout returning 'false' if the future is not complete"() {
    expect: !future.awaitUninterruptibly(50, TimeUnit.MILLISECONDS)
  }

  @Timeout(1)
  def "-awaitUninterruptibly(timeout) unlocks after the specified timeout (in ms) even if an interrupt occurs"() {
    given: scheduledEvent(100) { future.finishedSuccessfully("result", response) }
    and: scheduledInterruptForCurrentThread(50)
    expect: future.awaitUninterruptibly(200)
  }

  @Timeout(1)
  def "-awaitUninterruptibly(timeout) immediately returns if the future is already complete"() {
    given: future.finishedSuccessfully("result", response)
    expect: future.awaitUninterruptibly(200)
  }

  @Timeout(1)
  def "-awaitUninterruptibly(timeout) unlocks after timeout (in ms) returning 'false' if the future is not complete"() {
    expect: !future.awaitUninterruptibly(50)
  }

  def "it gracefully handles exceptions when notifiying listeners"() {
    given: "an attached listener which raises exception when notified"
    def misbehavingListener = new RequestFutureListener() {
      @Override void operationComplete(RequestFuture future) throws Exception { throw new Exception("kaboom") }
    }
    future.addListener(misbehavingListener)

    when: "the listener is notified of the future completion"
    future.finishedSuccessfully("finished", response)

    then: "no exception surfaces"
    noExceptionThrown()
  }

  def "-toString prints a descriptive output for each different state"() {
    given: "the string representation of an unfinished future"
    def inProgress = future.toString()

    and: "the string representation of a successfully finished future"
    future.finishedSuccessfully("finished", response)
    def success = future.toString()

    and: "the string representation of failed future"
    def failedFuture = new DefaultRequestFuture()
    failedFuture.failedWithCause(RequestFuture.CANCELLED)
    def failure = failedFuture.toString()

    expect: "them all to be different"
    inProgress != success
    inProgress != failure
    success != failure
  }

  private RequestFutureListener createListener() {
    new RequestFutureListener() {
      def notified = false
      @Override void operationComplete(RequestFuture future) throws Exception { notified = true }
    }
  }

  private static void scheduledInterruptForCurrentThread(long timeout) {
    def thread = Thread.currentThread()
    scheduledEvent(timeout) { thread.interrupt() }
  }

  private static void scheduledEvent(long timeout, Closure closure) {
    new Thread(new Runnable() {
      @Override void run() {
        sleep(timeout)
        closure.call()
      }
    }).start()
  }
}
