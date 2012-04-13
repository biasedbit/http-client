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

package com.biasedbit.http.client;

import com.biasedbit.http.event.ConnectionClosedEvent;
import com.biasedbit.http.event.ConnectionFailedEvent;
import com.biasedbit.http.event.ConnectionOpenEvent;
import com.biasedbit.http.event.EventType;
import com.biasedbit.http.event.ExecuteRequestEvent;
import com.biasedbit.http.event.HttpClientEvent;
import com.biasedbit.http.event.RequestCompleteEvent;

/**
 * Statistics gathering version of {@link DefaultHttpClient}.
 * <p/>
 * Overrides the {@code eventHandlingLoop()} method (with the exact same code) but adds a couple of time measurement
 * calls. Even though these add neglectable overhead, for production scenarios where stats gathering is not vital, you
 * should use {@link DefaultHttpClient} rather than this one.
 * <p/>
 * This is only useful if you're implementing your own {@link com.biasedbit.http.host.HostContext} or
 * {@link com.biasedbit.http.connection.HttpConnection} and want to test the impact of your changes.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class StatsGatheringHttpClient extends AbstractHttpClient
        implements EventProcessorStatsProvider {

    // internal vars --------------------------------------------------------------------------------------------------

    protected long totalTime            = 0;
    protected long executeRequestTime   = 0;
    protected long requestCompleteTime  = 0;
    protected long connectionOpenTime   = 0;
    protected long connectionClosedTime = 0;
    protected long connectionFailedTime = 0;
    protected int  events               = 0;

    // AbstractHttpClient ---------------------------------------------------------------------------------------------

    @Override
    public void eventHandlingLoop() {
        for (;;) {
            // Manual synchronization here because before removing an element, we first need to check whether an
            // active available connection exists to satisfy the request.
            try {
                HttpClientEvent event = eventQueue.take();
                if (event == POISON) {
                    this.eventConsumerLatch.countDown();
                    return;
                }
                this.events++;
                long start = System.nanoTime();

                switch (event.getEventType()) {
                    case EXECUTE_REQUEST:
                        this.handleExecuteRequest((ExecuteRequestEvent) event);
                        this.executeRequestTime += System.nanoTime() - start;
                        break;
                    case REQUEST_COMPLETE:
                        this.handleRequestComplete((RequestCompleteEvent) event);
                        this.requestCompleteTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_OPEN:
                        this.handleConnectionOpen((ConnectionOpenEvent) event);
                        this.connectionOpenTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_CLOSED:
                        this.handleConnectionClosed((ConnectionClosedEvent) event);
                        this.connectionClosedTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_FAILED:
                        this.handleConnectionFailed((ConnectionFailedEvent) event);
                        this.connectionFailedTime += System.nanoTime() - start;
                        break;
                    default:
                        // Consume and do nothing, unknown event.
                }
                this.totalTime += System.nanoTime() - start;
            } catch (InterruptedException e) {
                // ignore, poisoning the queue is the only way to stop
            }
        }

    }

    // EventProcessorStatsProvider ------------------------------------------------------------------------------------

    @Override
    public long getTotalExecutionTime() {
        return this.totalTime / 1000000;
    }

    @Override
    public long getEventProcessingTime(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return this.executeRequestTime / 1000000;
            case REQUEST_COMPLETE:
                return this.requestCompleteTime / 1000000;
            case CONNECTION_OPEN:
                return this.connectionOpenTime / 1000000;
            case CONNECTION_CLOSED:
                return this.connectionClosedTime / 1000000;
            case CONNECTION_FAILED:
                return this.connectionFailedTime / 1000000;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override
    public float getEventProcessingPercentage(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return (this.executeRequestTime / (float) this.totalTime) * 100;
            case REQUEST_COMPLETE:
                return (this.requestCompleteTime / (float) this.totalTime) * 100;
            case CONNECTION_OPEN:
                return (this.connectionOpenTime / (float) this.totalTime) * 100;
            case CONNECTION_CLOSED:
                return (this.connectionClosedTime / (float) this.totalTime) * 100;
            case CONNECTION_FAILED:
                return (this.connectionFailedTime / (float) this.totalTime) * 100;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override
    public long getProcessedEvents() {
        return this.events;
    }
}
