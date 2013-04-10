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

package com.biasedbit.http.client.event;

import com.biasedbit.http.client.connection.HttpConnection;

/**
 * Event generated when a new connection is successfully opened.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class ConnectionOpenEvent
        implements HttpClientEvent {

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpConnection connection;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConnectionOpenEvent(HttpConnection connection) {
        this.connection = connection;
    }

    // HttpClientEvent ------------------------------------------------------------------------------------------------

    @Override
    public EventType getEventType() {
        return EventType.CONNECTION_OPEN;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public HttpConnection getConnection() {
        return this.connection;
    }

    // object overrides -----------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder().append("ConnectionOpenEvent{").append(this.connection).append('}').toString();
    }
}
