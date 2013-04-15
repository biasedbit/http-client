package com.biasedbit.http.client.timeout

import com.biasedbit.http.client.future.RequestFuture
import com.biasedbit.http.client.processor.DiscardProcessor
import com.biasedbit.http.client.util.RequestContext
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executors

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class BasicTimeoutControllerSpec extends Specification {

  def controller = new BasicTimeoutController(0)

  def setup() { assert controller.init() }
  def cleanup() { controller.terminate() }

  def "it accepts N > 0 as constructor argument and creates a fixed size thread pool"() {
    expect: new BasicTimeoutController(1)
  }

  def "it doesn't terminate an external executor when its created with one"() {
    given: def executor = Executors.newCachedThreadPool()
    and: def controller = new BasicTimeoutController(executor)

    when: controller.terminate()
    then: !executor.terminated
  }

  @Unroll
  def "it times out a request when the time out is #timeout ms and #sleepTime ms have elapsed"() {
    given: def context = createContext(timeout)
    and: controller.controlTimeout(context);
    when: sleep(sleepTime)
    then: context.getFuture().isDone()
    and: !context.getFuture().isSuccessful()
    and: context.getFuture().getCause() == RequestFuture.TIMED_OUT

    where:
    timeout | sleepTime
    100     | 200
    150     | 200
    199     | 200
    200     | 210
  }

  @Unroll
  def "it doesn't time out a request when time out is #timeout ms and #sleepTime ms have elapsed"() {
    setup: def context = createContext(timeout)
    and: controller.controlTimeout(context);
    when: sleep(sleepTime)
    then: !context.getFuture().isDone()

    where:
    timeout | sleepTime
    500     | 100
    500     | 490
  }

  private static def createContext(int timeout) {
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/index")

    new RequestContext<>("biasedbit.com", 80, timeout, request, new DiscardProcessor());
  }
}
