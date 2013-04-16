package com.biasedbit.http.client.util

import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.group.ChannelGroup
import spock.lang.Specification

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class CleanupChannelGroupSpec extends Specification {

  ChannelGroup group

  def setup() { group = new CleanupChannelGroup("test-group") }

  def "-close closes all channels when called once"() {
    setup:
    def channel1 = channelMock(1)
    def channel2 = channelMock(2)
    group.add(channel1)
    group.add(channel2)

    when: group.close()

    then: 1 * channel1.close() >> channel1.getCloseFuture() // return the same mock
    and: 1 * channel2.close() >> channel1.getCloseFuture()
  }

  def "-close raises exception when called twice"() {
    setup: group.close()
    when: group.close()
    then: thrown(IllegalStateException)
  }

  def "-add adds a channel to the group"() {
    setup: def channel = channelMock(1)
    expect: group.add(channel)
    and: group.size() == 1
  }

  def "-add immediately closes a channel if the group has already been closed"() {
    setup:
    def channel = channelMock(1)
    1 * channel.close()
    group.close()

    expect: !group.add(channel)
  }

  private Channel channelMock(int channelId) {
    def channelFuture = Mock(ChannelFuture)

    Mock(Channel) {
      getId() >> channelId
      getCloseFuture() >> channelFuture
    }
  }
}
