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

import java.util.Collection;
import java.util.LinkedList;

/**
 * Helper class to hold both active connections and the number of connections opening to a given host.
 *
 * This class is <strong>not thread-safe</strong> and should only be updated by a thread at a time unless manual
 * external synchronization is used.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class ConnectionPool {

    // internal vars --------------------------------------------------------------------------------------------------

    private boolean connectionFailures;
    private int connectionsOpening;
    private final Collection<HttpConnection> connections;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConnectionPool() {
        this.connectionsOpening = 0;
        this.connections = new LinkedList<HttpConnection>();
    }

    // interface ------------------------------------------------------------------------------------------------------

    public void connectionOpening() {
        this.connectionsOpening++;
    }

    public void connectionFailed() {
        // Activate connection failure flag.
        this.connectionFailures = true;
        // Decrease opening connections indicator.
        this.connectionsOpening--;
    }

    public void connectionOpen(HttpConnection connection) {
        // Decrease opening connections indicator.
        if (this.connectionsOpening > 0) {
            this.connectionsOpening--;
        }
        // Reset connection failures.
        this.connectionFailures = false;
        // Add to pool.
        this.connections.add(connection);
    }

    public void connectionClosed(HttpConnection connection) {
        this.connections.remove(connection);
    }

    /**
     * Returns the number of both active and opening connections.
     *
     * @return The number of active connections + the number of pending connections.
     */
    public int getTotalConnections() {
        return this.connections.size() + this.connectionsOpening;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean hasConnectionFailures() {
        return connectionFailures;
    }

    public int getConnectionsOpening() {
        return connectionsOpening;
    }

    public Collection<HttpConnection> getConnections() {
        return connections;
    }
}
