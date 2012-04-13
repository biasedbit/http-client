/*
 * Copyright 2012 Bruno de Carvalho
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

package com.biasedbit.http.connection;

import com.biasedbit.http.timeout.TimeoutManager;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class PipeliningHttpConnectionFactory
        implements HttpConnectionFactory {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;
    private static final boolean ALLOW_POST_PIPELINING = false;
    private static final int MAX_REQUESTS_IN_PIPELINE = 50;

    // configuration --------------------------------------------------------------------------------------------------

    private boolean disconnectIfNonKeepAliveRequest;
    private boolean allowNonIdempotentPipelining;
    private int maxRequestsInPipeline;

    // constructors ---------------------------------------------------------------------------------------------------

    public PipeliningHttpConnectionFactory() {
        this.disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;
        this.allowNonIdempotentPipelining = ALLOW_POST_PIPELINING;
        this.maxRequestsInPipeline = MAX_REQUESTS_IN_PIPELINE;
    }

    // HttpConnectionFactory ------------------------------------------------------------------------------------------

    @Override
    public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                        TimeoutManager manager) {
        return this.createConnection(id, host, port, listener, manager, null);
    }

    @Override
    public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                        TimeoutManager manager, Executor executor) {
        PipeliningHttpConnection connection = new PipeliningHttpConnection(id, host, port, listener, manager, executor);
        connection.setDisconnectIfNonKeepAliveRequest(this.disconnectIfNonKeepAliveRequest);
        connection.setAllowNonIdempotentPipelining(this.allowNonIdempotentPipelining);
        connection.setMaxRequestsInPipeline(this.maxRequestsInPipeline);
        return connection;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isDisconnectIfNonKeepAliveRequest() {
        return disconnectIfNonKeepAliveRequest;
    }

    public void setDisconnectIfNonKeepAliveRequest(boolean disconnectIfNonKeepAliveRequest) {
        this.disconnectIfNonKeepAliveRequest = disconnectIfNonKeepAliveRequest;
    }

    public boolean isAllowNonIdempotentPipelining() {
        return allowNonIdempotentPipelining;
    }

    public void setAllowNonIdempotentPipelining(boolean allowNonIdempotentPipelining) {
        this.allowNonIdempotentPipelining = allowNonIdempotentPipelining;
    }

    public int getMaxRequestsInPipeline() {
        return maxRequestsInPipeline;
    }

    public void setMaxRequestsInPipeline(int maxRequestsInPipeline) {
        this.maxRequestsInPipeline = maxRequestsInPipeline;
    }
}
