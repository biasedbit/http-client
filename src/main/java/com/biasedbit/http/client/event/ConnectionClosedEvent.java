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

import com.biasedbit.http.client.connection.Connection;
import com.biasedbit.http.client.util.RequestContext;
import lombok.*;

import java.util.Collection;

/**
 * Event generated when an established connection is closed.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@RequiredArgsConstructor
@ToString
public class ConnectionClosedEvent
        implements ClientEvent {

    // internal vars --------------------------------------------------------------------------------------------------

    @Getter private final Connection                 connection;
    @Getter private final Collection<RequestContext> retryRequests;

    // ClientEvent ------------------------------------------------------------------------------------------------

    @Override public EventType getEventType() { return EventType.CONNECTION_CLOSED; }

    // interface ------------------------------------------------------------------------------------------------------

    public boolean hasRequestsToRetry() { return (retryRequests != null) && !retryRequests.isEmpty(); }
}
