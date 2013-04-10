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

import lombok.Getter;

import java.util.Collection;
import java.util.LinkedList;

/**
 * Helper class to hold both active connections and the number of connections opening to a given host.
 * <p/>
 * This class is <strong>not thread-safe</strong> and should only be updated by a thread at a time unless manual
 * external synchronization is used.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class ConnectionPool {

    // properties -----------------------------------------------------------------------------------------------------

    @Getter private final Collection<HttpConnection> connections;

    // internal vars --------------------------------------------------------------------------------------------------

    private boolean connectionFailures;
    private int     connectionsOpening;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConnectionPool() {
        connectionsOpening = 0;
        connections = new LinkedList<>();
    }

    // interface ------------------------------------------------------------------------------------------------------

    public void connectionOpening() { connectionsOpening++; }

    public void connectionFailed() {
        // Activate connection failure flag.
        connectionFailures = true;
        // Decrease opening connections indicator.
        connectionsOpening--;
    }

    public void connectionOpen(HttpConnection connection) {
        // Decrease opening connections indicator.
        if (connectionsOpening > 0) connectionsOpening--;

        // Reset connection failures.
        connectionFailures = false;
        // Add to pool.
        connections.add(connection);
    }

    public void connectionClosed(HttpConnection connection) { connections.remove(connection); }

    /**
     * Returns the number of both active and opening connections.
     *
     * @return The number of active connections + the number of pending connections.
     */
    public int totalConnections() { return connections.size() + connectionsOpening; }

    public boolean hasConnectionFailures() { return connectionFailures; }

    public boolean hasConnections() { return !connections.isEmpty(); }
}
