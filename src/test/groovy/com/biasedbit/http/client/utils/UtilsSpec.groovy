package com.biasedbit.http.client.utils

import spock.lang.Specification

import static com.biasedbit.http.client.util.Utils.*

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class UtilsSpec extends Specification {

  def "#ensureValue throws IllegalArgumentException if condition evaluates to false"() {
    when: ensureValue(false, "cause")
    then: def e = thrown(IllegalArgumentException)
    and: e.message == "cause"
  }

  def "#ensureValue does not throw any exception if condition evaluates to true"() {
    when: ensureValue(true, "cause")
    then: noExceptionThrown()
  }

  def "#ensureState throws IllegalStateException if condition evaluates to false"() {
    when: ensureState(false, "cause")
    then: def e = thrown(IllegalStateException)
    and: e.message == "cause"
  }

  def "#ensureState throws IllegalStateException with formatted description if condition evaluates to false"() {
    when: ensureState(false, "a %s exception", "formatted")
    then: def e = thrown(IllegalStateException)
    and: e.message == "a formatted exception"
  }

  def "#ensureState does not throw any exception if condition evaluates to true"() {
    when: ensureState(true, "cause")
    then: noExceptionThrown()
  }

  def "#string concatenates multiple strings"() {
    expect: string("a", "string", "in", "parts") == "astringinparts"
  }
}
