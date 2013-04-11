package com.biasedbit.http.client.timeout

import com.biasedbit.http.client.HostContextTestUtil
import com.biasedbit.http.client.HttpRequestContext
import com.biasedbit.http.client.future.DefaultHttpRequestFuture
import com.biasedbit.http.client.future.HttpRequestFuture
import com.biasedbit.http.client.processor.DiscardProcessor
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class HashedWheelTimeoutControllerSpec extends Specification {

  TimeoutController controller

  def setup() {
    controller = new HashedWheelTimeoutController(100, TimeUnit.MILLISECONDS, 512)
    assert controller.init()
  }

  def cleanup() { controller.terminate() }

  @Unroll
  def "it times out a request when the time out is #timeout ms and #sleepTime ms have elapsed"() {
    setup: def context = createContext(timeout)
    and: controller.controlTimeout(context);
    when: sleep(sleepTime)
    then: context.getFuture().isDone()
    and: !context.getFuture().isSuccess()
    and: context.getFuture().getCause() == HttpRequestFuture.TIMED_OUT

    where:
    timeout | sleepTime
    100     | 200
    150     | 200
    199     | 200
  }

  @Unroll
  def "it doesn't time out a request when time out is #timeout ms and #sleepTime ms have elapsed"() {
    setup: def context = createContext(timeout)
    and: controller.controlTimeout(context);
    when: sleep(sleepTime)
    then: !context.getFuture().isDone()

    where:
    timeout | sleepTime
    450     | 490 // because the timer ticks every 100ms
    500     | 100
    500     | 490
  }

  private static def createContext(int timeout) {
    def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/index")

    new HttpRequestContext<>("biasedbit.com", 80, timeout, request,
        new DiscardProcessor(), new DefaultHttpRequestFuture<>());
  }
}
