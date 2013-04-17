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

import com.biasedbit.http.client.util.RequestContext;

import java.util.Collection;

/**
 * {@link Connection} listener.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface ConnectionListener {

    /**
     * Connection opened event, called by the {@link Connection} when a requested connection establishes.
     *
     * @param connection Connection that just established.
     */
    void connectionOpened(Connection connection);

    /**
     * Connection terminated event, called by the {@link Connection} when an active connection disconnects.
     *
     * This is the event that {@link Connection}s that support submission of multiple parallel requests call to
     * signal disconnection, since some of the requests may still be executed in another connection (e.g. a pipelining
     * connection that goes down after a couple of requests but still has some more idempotent requests queued).
     *
     * @param connection Connection that was disconnected.
     * @param retryRequests List of pending submitted requests that should be retried in a new connection, if possible.
     */
    void connectionTerminated(Connection connection, Collection<RequestContext> retryRequests);

    /**
     * Connection terminated event, called by the {@link Connection} when an active connection disconnects.
     *
     * This is the event that {@link Connection}s that only support a single request at a time use to signal
     * disconnection. It can also be used by {@link Connection}s that support submission of multiple parallel
     * requests (e.g. a pipelining connection) when they disconnect and have no requests that should be retried (i.e.
     * all pipelined requests executed successfully).
     *
     * @param connection Connection that was disconnected.
     */
    void connectionTerminated(Connection connection);

    /**
     * Connection failed event, called by the {@link Connection} when a connection attempt fails.
     *
     * @param connection Connection that failed.
     */
    void connectionFailed(Connection connection);

    /**
     * Request complete event, called by the {@link Connection} when a response to a request allocated to it is
     * either received or fails for some reason.
     *
     * @param connection Connection in which the event finished.
     * @param context Request context containing the request that has completed.
     */
    void requestFinished(Connection connection, RequestContext context);
}
