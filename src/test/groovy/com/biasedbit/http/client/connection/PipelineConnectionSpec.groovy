package com.biasedbit.http.client.connection

import com.biasedbit.http.client.timeout.TimeoutController
import spock.lang.Specification

import java.util.concurrent.Executor

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
class PipelineConnectionSpec extends AbstractConnectionTest {

  @Override protected PipeliningConnection createConnection(String host, int port, ConnectionListener listener,
                                                            TimeoutController timeoutController, Executor executor) {
    return new PipeliningConnection("${host}:${port}", host, port, listener, timeoutController, executor)
  }
}
