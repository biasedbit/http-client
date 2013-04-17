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

package com.biasedbit.http.client;

import com.biasedbit.http.client.event.*;
import lombok.Getter;

/**
 * Statistics gathering version of {@link DefaultHttpClient}.
 * <p/>
 * Overrides the {@code eventHandlingLoop()} method (with the exact same code) but adds a couple of time measurement
 * calls. Even though these add neglectable overhead, for production scenarios where stats gathering is not vital, you
 * should use {@link DefaultHttpClient} rather than this one.
 * <p/>
 * This is only useful if you're implementing your own {@link com.biasedbit.http.client.util.HostController} or
 * {@link com.biasedbit.http.client.connection.Connection} and want to test the impact of your changes.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class StatsGatheringHttpClient
        extends DefaultHttpClient
        implements EventProcessorStatsProvider {

    // properties -----------------------------------------------------------------------------------------------------

    @Getter private long totalTime            = 0;
    @Getter private long executeRequestTime   = 0;
    @Getter private long requestCompleteTime  = 0;
    @Getter private long connectionOpenTime   = 0;
    @Getter private long connectionClosedTime = 0;
    @Getter private long connectionFailedTime = 0;
    @Getter private int  events               = 0;

    // internal vars --------------------------------------------------------------------------------------------------

    private long executeRequestCount;
    private long requestCompleteCount;
    private long connectionOpenCount;
    private long connectionClosedCount;
    private long connectionFailedCount;

    // DefaultHttpClient ----------------------------------------------------------------------------------------------

    @Override public void eventHandlingLoop() {
        while (true) {
            try {
                ClientEvent event = popNextEvent();
                if (event == POISON) {
                    eventQueuePoisoned();
                    return;
                }
                events++;
                long start = System.nanoTime();

                switch (event.getEventType()) {
                    case EXECUTE_REQUEST:
                        handleExecuteRequest((ExecuteRequestEvent) event);
                        executeRequestCount++;
                        executeRequestTime += System.nanoTime() - start;
                        break;
                    case REQUEST_COMPLETE:
                        handleRequestComplete((RequestCompleteEvent) event);
                        requestCompleteCount++;
                        requestCompleteTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_OPEN:
                        handleConnectionOpen((ConnectionOpenEvent) event);
                        connectionOpenCount++;
                        connectionOpenTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_CLOSED:
                        handleConnectionClosed((ConnectionClosedEvent) event);
                        connectionClosedCount++;
                        connectionClosedTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_FAILED:
                        handleConnectionFailed((ConnectionFailedEvent) event);
                        connectionFailedCount++;
                        connectionFailedTime += System.nanoTime() - start;
                        break;
                    default: // Consume and do nothing, unknown event.
                }
                totalTime += System.nanoTime() - start;
            } catch (InterruptedException ignored) {  /* poisoning the queue is the only way to stop */ }
        }
    }

    // EventProcessorStatsProvider ------------------------------------------------------------------------------------

    @Override public long getTotalExecutionTime() { return totalTime / 1000000; }

    @Override public long getEventProcessingTime(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST: return executeRequestTime / 1000000;
            case REQUEST_COMPLETE: return requestCompleteTime / 1000000;
            case CONNECTION_OPEN: return connectionOpenTime / 1000000;
            case CONNECTION_CLOSED: return connectionClosedTime / 1000000;
            case CONNECTION_FAILED: return connectionFailedTime / 1000000;
            default: throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override public float getEventProcessingPercentage(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST: return (executeRequestTime / (float) totalTime) * 100;
            case REQUEST_COMPLETE: return (requestCompleteTime / (float) totalTime) * 100;
            case CONNECTION_OPEN: return (connectionOpenTime / (float) totalTime) * 100;
            case CONNECTION_CLOSED: return (connectionClosedTime / (float) totalTime) * 100;
            case CONNECTION_FAILED: return (connectionFailedTime / (float) totalTime) * 100;
            default: throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override public long getProcessedEvents() { return events; }

    @Override public long getProcessedEvents(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST: return executeRequestCount;
            case REQUEST_COMPLETE: return requestCompleteCount;
            case CONNECTION_OPEN: return connectionOpenCount;
            case CONNECTION_CLOSED: return connectionClosedCount;
            case CONNECTION_FAILED: return connectionFailedCount;
            default: throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }
}
