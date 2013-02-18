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

package com.biasedbit.http.host;

import com.biasedbit.http.HttpRequestContext;
import com.biasedbit.http.connection.ConnectionPool;

import java.util.Collection;
import java.util.Queue;

/**
 * HostContexts store context on a per-host basis.
 * It serves as a helper component to help keep HttpClient implementations cleaner.
 *
 * Also, it is up to implementations of this interface to determine exactly how they drain the queue.
 * By returning different values, they can influence how the HttpClient to behaves (e.g.: they can request a new
 * connection, they can drain 1 element, they can drain multiple elements, etc).
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface HostContext {

    public enum DrainQueueResult {
        QUEUE_EMPTY,
        DRAINED,
        NOT_DRAINED,
        OPEN_CONNECTION,
    }

    String getHost();

    int getPort();

    ConnectionPool getConnectionPool();

    Queue<HttpRequestContext> getQueue();

    /**
     * Used to restore requests to the head of the queue.
     *
     * An example of the usage of this method is when a pipelining HTTP connection disconnects unexpectedly while some
     * of the requests were still waiting for a response. Instead of simply failing those requests, they can be retried
     * in a different connection, provided that their execution order is maintained (i.e. they go back to the head of
     * the queue and not the tail).
     *
     * @param requests Collection of requests to add to the queue head.
     */
    void restoreRequestsToQueue(Collection<HttpRequestContext> requests);

    /**
     * Adds a request to the end of the queue.
     *
     * @param request Request to add.
     */
    void addToQueue(HttpRequestContext request);

    /**
     * Drains one (or more) elements of the queue into one (or more) connections in the connection pool.
     *
     * This is the method used to move queue elements into connections and thus advance the request dispatching process.
     *
     * @return The result of the drain operation.
     */
    DrainQueueResult drainQueue();

    /**
     * Retrieves the first element of the queue (head).
     *
     * @return The first element of the queue.
     */
    HttpRequestContext pollQueue();

    /**
     * Fails all queued requests with the given cause.
     *
     * @param cause Cause to fail all queued requests.
     * @return 
     */
    int failAllRequests(Throwable cause);
}
