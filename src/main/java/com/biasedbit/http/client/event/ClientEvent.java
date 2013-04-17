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
 * An generic event that is consumed by the consumer thread in {@link com.biasedbit.http.client.DefaultHttpClient}.
 * <p/>
 * When an event is consumed it will generate actions, such as executing requests, opening connections, queueing
 * requests, etc.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface ClientEvent {

    /**
     * @return Type of the event.
     */
    EventType getEventType();
}
