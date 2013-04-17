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

/**
 * Definition of possible event types for {@link com.biasedbit.http.client.DefaultHttpClient}'s consumer thread.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public enum EventType {

    /**
     * A new request execution call was issued.
     */
    EXECUTE_REQUEST,
    /**
     * A request execution was completeed (successfully or not).
     */
    REQUEST_COMPLETE,
    /**
     * A new connection to a given host was opened.
     */
    CONNECTION_OPEN,
    /**
     * An existing connection to a host was closed.
     */
    CONNECTION_CLOSED,
    /**
     * An attempt to connect to a given host failed.
     */
    CONNECTION_FAILED,
}
