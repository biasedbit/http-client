package com.biasedbit.http.client.util

import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class NamedThreadFactorySpec extends Specification {

  def "it creates normal priority non-daemon threads with the configured prefix"() {
    setup:
    def factory = new NamedThreadFactory("test-prefix")
    def runnable = new Runnable() {
      @Override void run() { }
    }

    expect: with(factory.newThread(runnable)) { Thread thread ->
      thread.priority == Thread.NORM_PRIORITY
      !thread.isDaemon()
      thread.name.startsWith("test-prefix")
    }
  }
}
