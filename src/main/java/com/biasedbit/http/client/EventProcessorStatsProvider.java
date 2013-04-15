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

import com.biasedbit.http.client.event.EventType;

/**
 * Provides statistics for an event processor.
 * <p/>
 * This is used mostly for development stages, to compare improvements of modifications.
 * <p/>
 * @see StatsGatheringHttpClient
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface EventProcessorStatsProvider {

    /**
     * @return Total execution time of the event processor, in milliseconds.
     */
    long getTotalExecutionTime();

    /**
     * @param event Type of event to query for.
     *
     * @return Processing time for events of given type, in milliseconds.
     */
    long getEventProcessingTime(EventType event);

    /**
     * @param event Type of event to query for.
     *
     * @return Processing percentage (ratio) of a given type, between 0 and 100
     */
    float getEventProcessingPercentage(EventType event);

    /**
     * @return Total of processed events.
     */
    long getProcessedEvents();

    long getProcessedEvents(EventType event);
}

