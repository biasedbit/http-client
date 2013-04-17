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

package com.biasedbit.http.client.util;

import com.biasedbit.http.client.connection.Connection;
import lombok.*;

import java.util.Collection;
import java.util.LinkedList;

import static com.biasedbit.http.client.util.HostController.DrainQueueResult.*;
import static com.biasedbit.http.client.util.Utils.ensureValue;

/**
 * HostController stores context on a per-host basis, serving as a helper component to help keep HttpClient
 * implementations cleaner.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@RequiredArgsConstructor
public class HostController {

    // enums ----------------------------------------------------------------------------------------------------------

    public static enum DrainQueueResult {QUEUE_EMPTY, DRAINED, NOT_DRAINED, OPEN_CONNECTION}

    // properties -----------------------------------------------------------------------------------------------------

    @Getter private final String host;
    @Getter private final int    port;

    @Delegate private final ConnectionPool connectionPool; // exposes connection pool API on this class

    // internal vars --------------------------------------------------------------------------------------------------

    protected final LinkedList<RequestContext> queue = new LinkedList<>();

    // class interface ------------------------------------------------------------------------------------------------

    public static HostController createContextForRequest(RequestContext context, int maxConnections) {
        return new HostController(context.getHost(), context.getPort(), new ConnectionPool(maxConnections));
    }

    // interface ------------------------------------------------------------------------------------------------------

    public boolean isCleanable() { return !connectionPool.hasConnections() && queue.isEmpty(); }

    /**
     * Used to restore requests to the head of the queue.
     * <p/>
     * An example of the usage of this method is when a pipelining HTTP connection disconnects unexpectedly while some
     * of the requests were still waiting for a response. Instead of simply failing those requests, they can be retried
     * in a different connection, provided that their execution order is maintained (i.e. they go back to the head of
     * the queue and not the tail).
     *
     * @param requests Collection of requests to add to the queue head.
     */
    public void restoreRequestsToQueue(Collection<RequestContext> requests) { queue.addAll(0, requests); }

    /**
     * Adds a request to the end of the queue.
     *
     * @param request Request to add.
     */
    public void addToQueue(RequestContext request) {
        ensureValue(request.getHost().equals(host),
                    "Request host (%s) and context host (%s) are different", request.getHost(), host);
        ensureValue(request.getPort() == port,
                    "Request port (%s) and context port (%s) are different", request.getPort(), port);

        queue.add(request);
    }

    /**
     * Drains one (or more) elements of the queue into one (or more) connections in the connection pool.
     * <p/>
     * This is the method used to move queue elements into connections and thus advance the request dispatching process.
     *
     * @return The result of the drain operation.
     */
    public DrainQueueResult drainQueue() {
        // 1. Test if there's anything to drain
        if (queue.isEmpty()) return QUEUE_EMPTY;

        // 2. There are contents to drain, test if there are any connections created.
        if (!connectionPool.hasConnections()) {
            // 2a. No connections open, test if there is still room to create a new one.
            if (connectionPool.hasAvailableSlots()) return OPEN_CONNECTION;
            else return NOT_DRAINED;
        }

        // 3. There is content to drain and there are connections, drain as much as possible in a single loop.
        boolean drained = false;
        for (Connection connection : connectionPool.getConnections()) {
            // Connection not available, immediately try next one
            if (!connection.isAvailable()) continue;

            // Feed requests off the queue to the connection until it stops reporting itself as available or
            // execution submission fails.
            boolean executionAccepted = false;
            do {
                // Peek the next request and see if the connection is able to accept it.
                RequestContext context = queue.peek();
                executionAccepted = connection.execute(context);
                if (executionAccepted) {
                    // Request was accepted by the connection, remove it from the queue.
                    queue.remove();
                    // Prematurely exit in case there are no further requests to execute.
                    if (queue.isEmpty()) return DRAINED;

                    // Otherwise, result will be DRAINED whether we manage do execute another request or not.
                    drained = true;
                }
            } while (connection.isAvailable() && executionAccepted);
        }
        if (drained) return DRAINED;

        // 4. There were connections open but none of them was available; if possible, request a new one.
        if (connectionPool.hasAvailableSlots()) return OPEN_CONNECTION;
        else return NOT_DRAINED;
    }

    /**
     * Retrieves the first element of the queue (head).
     *
     * @return The first element of the queue.
     */
    public RequestContext pollQueue() { return queue.poll(); }

    /**
     * Fails all queued requests with the given cause.
     *
     * @param cause Cause to fail all queued requests.
     */
    public void failAllRequests(Throwable cause) {
        for (RequestContext context : queue) context.getFuture().failedWithCause(cause);

        queue.clear();
    }

    public void shutdown(Throwable cause) {
        failAllRequests(cause);

        for (Connection connection : connectionPool.getConnections()) connection.terminate(cause);
    }
}
