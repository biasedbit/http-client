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

package com.biasedbit.http.client.host;

import com.biasedbit.http.client.HttpRequestContext;
import com.biasedbit.http.client.connection.ConnectionPool;
import com.biasedbit.http.client.connection.HttpConnection;
import com.biasedbit.http.client.util.Utils;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.LinkedList;

import static com.biasedbit.http.client.host.HostContext.DrainQueueResult.*;
import static com.biasedbit.http.client.util.Utils.*;

/**
 * Abstract implementation of the {@link HostContext} interface.
 * <p/>
 * This class contains boilerplate code that all implementations of {@link HostContext} would surely have as well.
 * The important logic is present in {@link #drainQueue()}, method that needs to be implemented by extensions of this
 * class.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@RequiredArgsConstructor
public class DefaultHostContext
        implements HostContext {

    // internal vars --------------------------------------------------------------------------------------------------

    protected final String         host;
    protected final int            port;
    protected final ConnectionPool connectionPool;

    protected final LinkedList<HttpRequestContext> queue = new LinkedList<>();

    // HostContext ----------------------------------------------------------------------------------------------------

    @Override public String getHost() { return host; }

    @Override public int getPort() { return port; }

    @Override public ConnectionPool getConnectionPool() { return connectionPool; }

    @Override public boolean isCleanable() { return !connectionPool.hasConnections() && queue.isEmpty(); }

    @Override public void restoreRequestsToQueue(Collection<HttpRequestContext> requests) { queue.addAll(0, requests); }

    @Override public void addToQueue(HttpRequestContext request) {
        ensureValue(request.getHost().equals(host),
                    "Request host (%s) and context host (%s) are different", request.getHost(), host);
        ensureValue(request.getPort() == port,
                    "Request port (%s) and context port (%s) are different", request.getPort(), port);

        queue.add(request);
    }

    @Override public DrainQueueResult drainQueue() {
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
        for (HttpConnection connection : connectionPool.getConnections()) {
            // Connection not available, immediately try next one
            if (!connection.isAvailable()) continue;

            // Feed requests off the queue to the connection until it stops reporting itself as available or
            // execution submission fails.
            boolean executionAccepted = false;
            do {
                // Peek the next request and see if the connection is able to accept it.
                HttpRequestContext context = queue.peek();
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

    @Override public HttpRequestContext pollQueue() { return queue.poll(); }

    @Override public void failAllRequests(Throwable cause) {
        for (HttpRequestContext context : queue) context.getFuture().failedWithCause(cause);

        queue.clear();
    }

    @Override public void terminateAllConnections(Throwable cause) {
        for (HttpConnection connection : connectionPool.getConnections()) connection.terminate(cause);
    }
}
