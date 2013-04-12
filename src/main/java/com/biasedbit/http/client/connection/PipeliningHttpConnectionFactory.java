/*
 * Copyright 2013 BiasedBit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.biasedbit.http.client.connection;

import com.biasedbit.http.client.timeout.TimeoutController;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.Executor;

import static com.biasedbit.http.client.connection.PipeliningHttpConnection.*;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class PipeliningHttpConnectionFactory
        implements HttpConnectionFactory {

    // properties -----------------------------------------------------------------------------------------------------

    @Getter @Setter private boolean disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;
    @Getter @Setter private boolean allowNonIdempotentPipelining    = ALLOW_POST_PIPELINING;
    @Getter @Setter private int     maxRequestsInPipeline           = MAX_REQUESTS_IN_PIPELINE;

    // HttpConnectionFactory ------------------------------------------------------------------------------------------

    @Override public PipeliningHttpConnection createConnection(String id, String host, int port,
                                                               HttpConnectionListener listener,
                                                               TimeoutController timeoutController, Executor executor) {
        PipeliningHttpConnection connection = new PipeliningHttpConnection(id, host, port, listener,
                                                                           timeoutController, executor);
        connection.setDisconnectIfNonKeepAliveRequest(disconnectIfNonKeepAliveRequest);
        connection.setAllowNonIdempotentPipelining(allowNonIdempotentPipelining);
        connection.setMaxRequestsInPipeline(maxRequestsInPipeline);

        return connection;
    }
}
