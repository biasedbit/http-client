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

import java.util.*;

import static com.biasedbit.http.client.host.HostContext.DrainQueueResult.*;

/**
 * Abstract implementation of the {@link HostContext} interface.
 * <p/>
 * This class contains boilerplate code that all implementations of {@link HostContext} would surely have as well.
 * The important logic is present in {@link #drainQueue()}, method that needs to be implemented by extensions of this
 * class.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHostContext
        implements HostContext {

    // internal vars --------------------------------------------------------------------------------------------------

    protected final String host;
    protected final int    port;
    protected final int    maxConnections;

    protected final ConnectionPool                 connectionPool = new ConnectionPool();
    protected final LinkedList<HttpRequestContext> queue          = new LinkedList<>();

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHostContext(String host, int port, int maxConnections) {
        Utils.ensureValue(maxConnections > 0, "maxConnections must be > 0");

        this.host = host;
        this.port = port;
        this.maxConnections = maxConnections;
    }

    // HostContext ----------------------------------------------------------------------------------------------------

    @Override public String getHost() { return host; }

    @Override public int getPort() { return port; }

    @Override public ConnectionPool getConnectionPool() { return connectionPool; }

    @Override public Queue<HttpRequestContext> getQueue() { return queue; }

    @Override public void restoreRequestsToQueue(Collection<HttpRequestContext> requests) { queue.addAll(0, requests); }

    @Override public void addToQueue(HttpRequestContext request) { queue.add(request); }

    @Override public DrainQueueResult drainQueue() {
        // 1. Test if there's anything to drain
        if (queue.isEmpty()) return QUEUE_EMPTY;

        // 2. There are contents to drain, test if there are any connections created.
        if (!connectionPool.hasConnections()) {
            // 2a. No connections open but there may still be connections opening, so we need to test if there is
            // still room to create a new one.
            if (connectionPool.totalConnections() < maxConnections) return OPEN_CONNECTION;
            else return NOT_DRAINED;
        }

        // 3. There is content to drain and there are connections, iterate them to find an available one.
        for (HttpConnection connection : connectionPool.getConnections()) {
            while (connection.isAvailable()) {
                // Found an available connection; peek the first request and attempt to execute it.
                HttpRequestContext context = queue.peek();
                if (connection.execute(context)) {
                    // If the request was executed it means the connection wasn't terminating and it's still connected.
                    // Remove it from the queue (it was only previously peeked) and return DRAINED.
                    queue.remove();
                    return DRAINED;
                }
            }
        }

        // 4. There were connections open but none of them was available; if possible, request a new one.
        if (connectionPool.totalConnections() < maxConnections) return OPEN_CONNECTION;
        else return NOT_DRAINED;
    }

    @Override public HttpRequestContext pollQueue() { return queue.poll(); }

    @Override public void failAllRequests(Throwable cause) {
        for (HttpRequestContext context : queue) context.getFuture().failedWithCause(cause);

        queue.clear();
    }
}
