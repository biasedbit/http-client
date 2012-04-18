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

import com.biasedbit.http.connection.DefaultHttpConnectionFactory;
import com.biasedbit.http.connection.HttpConnectionFactory;
import com.biasedbit.http.future.HttpRequestFutureFactory;
import com.biasedbit.http.host.DefaultHostContextFactory;
import com.biasedbit.http.host.HostContextFactory;
import com.biasedbit.http.ssl.SslContextFactory;
import com.biasedbit.http.timeout.TimeoutManager;

/**
 * Creates subclasses of {@link AbstractHttpClient} depending on configuration parameters.
 * <p/>
 * If this factory is configured with {@linkplain #setDebug(boolean) debug} enabled, it will generate instances of
 * {@link VerboseHttpClient}.
 * <p/>
 * If this factory is configured with {@linkplain #setGatherEventHandlingStats(boolean) statistics gathering}, it will
 * generate instances of {@link StatsGatheringHttpClient} (unless {@linkplain #setDebug(boolean) debug} is enabled,
 * which takes precedence.
 * <p/>
 * If neither of {@linkplain #setDebug(boolean) debug} or {@linkplain #setGatherEventHandlingStats(boolean) statistics
 * gathering} are enabled, then it generates instances of {@link DefaultHttpClient}.
 * <p/>
 * <strong>For the meaning of the configuration parameters, please take a look at {@link AbstractHttpClient}'s setter
 * methods.</strong>
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHttpClientFactory
        implements HttpClientFactory {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean               DEBUG                              = false;
    private static final boolean               GATHER_EVENT_HANDLING_STATS        = false;
    private static final boolean               USE_SSL                            = false;
    private static final int                   REQUEST_COMPRESSION_LEVEL          = 0;
    private static final boolean               AUTOMATIC_INFLATE                  = false;
    private static final int                   REQUEST_CHUNK_SIZE                 = 8192;
    private static final boolean               AGGREGATE_CHUNKS                   = false;
    private static final int                   CONNECTION_TIMEOUT_IN_MILLIS       = 2000;
    private static final int                   REQUEST_TIMEOUT_IN_MILLIS          = 2000;
    private static final int                   MAX_CONNECTIONS_PER_HOST           = 3;
    private static final int                   MAX_QUEUED_REQUESTS                = Short.MAX_VALUE;
    private static final boolean               USE_NIO                            = true;
    private static final int                   MAX_IO_WORKER_THREADS              = 50;
    private static final int                   MAX_EVENT_PROCESSOR_HELPER_THREADS = 20;
    private static final HostContextFactory    HOST_CONTEXT_FACTORY               = new DefaultHostContextFactory();
    private static final HttpConnectionFactory CONNECTION_FACTORY                 = new DefaultHttpConnectionFactory();
    private static final boolean               CLEANUP_INACTIVE_HOST_CONTEXTS     = true;
    private static final SslContextFactory     SSL_CONTEXT_FACTORY                = null;

    // configuration --------------------------------------------------------------------------------------------------

    private boolean                  debug;
    private boolean                  gatherEventHandlingStats;
    private boolean                  useSsl;
    private int                      requestCompressionLevel;
    private boolean                  automaticInflate;
    private int                      requestChunkSize;
    private boolean                  aggregateChunks;
    private int                      connectionTimeoutInMillis;
    private int                      requestTimeoutInMillis;
    private int                      maxConnectionsPerHost;
    private int                      maxQueuedRequests;
    private boolean                  useNio;
    private int                      maxIoWorkerThreads;
    private int                      maxEventProcessorHelperThreads;
    private HostContextFactory       hostContextFactory;
    private HttpConnectionFactory    connectionFactory;
    private HttpRequestFutureFactory futureFactory;
    private TimeoutManager           timeoutManager;
    private boolean                  cleanupInactiveHostContexts;
    private SslContextFactory        sslContextFactory;

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHttpClientFactory() {
        this.debug = DEBUG;
        this.gatherEventHandlingStats = GATHER_EVENT_HANDLING_STATS;
        this.useSsl = USE_SSL;
        this.requestCompressionLevel = REQUEST_COMPRESSION_LEVEL;
        this.automaticInflate = AUTOMATIC_INFLATE;
        this.requestChunkSize = REQUEST_CHUNK_SIZE;
        this.aggregateChunks = AGGREGATE_CHUNKS;
        this.connectionTimeoutInMillis = CONNECTION_TIMEOUT_IN_MILLIS;
        this.requestTimeoutInMillis = REQUEST_TIMEOUT_IN_MILLIS;
        this.maxConnectionsPerHost = MAX_CONNECTIONS_PER_HOST;
        this.maxQueuedRequests = MAX_QUEUED_REQUESTS;
        this.hostContextFactory = HOST_CONTEXT_FACTORY;
        this.connectionFactory = CONNECTION_FACTORY;
        this.useNio = USE_NIO;
        this.maxIoWorkerThreads = MAX_IO_WORKER_THREADS;
        this.maxEventProcessorHelperThreads = MAX_EVENT_PROCESSOR_HELPER_THREADS;
        this.cleanupInactiveHostContexts = CLEANUP_INACTIVE_HOST_CONTEXTS;
    }

    // HttpClientFactory ----------------------------------------------------------------------------------------------

    @Override
    public HttpClient getClient() {
        AbstractHttpClient client;
        if (this.debug) {
            client = new VerboseHttpClient();
        } else if (this.gatherEventHandlingStats) {
            client = new StatsGatheringHttpClient();
        } else {
            client = new DefaultHttpClient();
        }
        client.setUseSsl(this.useSsl);
        client.setRequestCompressionLevel(this.requestCompressionLevel);
        client.setAutoInflate(this.automaticInflate);
        client.setRequestChunkSize(this.requestChunkSize);
        client.setAggregateResponseChunks(this.aggregateChunks);
        client.setConnectionTimeoutInMillis(this.connectionTimeoutInMillis);
        client.setRequestTimeoutInMillis(this.requestTimeoutInMillis);
        client.setMaxConnectionsPerHost(this.maxConnectionsPerHost);
        client.setMaxQueuedRequests(this.maxQueuedRequests);
        client.setUseNio(this.useNio);
        client.setMaxIoWorkerThreads(this.maxIoWorkerThreads);
        client.setMaxEventProcessorHelperThreads(this.maxEventProcessorHelperThreads);
        client.setHostContextFactory(this.hostContextFactory);
        client.setConnectionFactory(this.connectionFactory);
        client.setTimeoutManager(this.timeoutManager);
        client.setCleanupInactiveHostContexts(this.cleanupInactiveHostContexts);
        client.setSslContextFactory(this.sslContextFactory);
        return client;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isGatherEventHandlingStats() {
        return gatherEventHandlingStats;
    }

    public void setGatherEventHandlingStats(boolean gatherEventHandlingStats) {
        this.gatherEventHandlingStats = gatherEventHandlingStats;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public int getRequestCompressionLevel() {
        return requestCompressionLevel;
    }

    public void setRequestCompressionLevel(int requestCompressionLevel) {
        if ((requestCompressionLevel < 0) || (requestCompressionLevel > 9)) {
            throw new IllegalArgumentException("RequestCompressionLevel must be in range [0;9] (0 = none, 9 = max)");
        }
        this.requestCompressionLevel = requestCompressionLevel;
    }

    public boolean isAutomaticInflate() {
        return automaticInflate;
    }

    public void setAutomaticInflate(boolean automaticInflate) {
        this.automaticInflate = automaticInflate;
    }

    public int getRequestChunkSize() {
        return requestChunkSize;
    }

    public void setRequestChunkSize(int requestChunkSize) {
        if (requestChunkSize < 128) {
            throw new IllegalArgumentException("Minimum accepted chunk size is 128b");
        }
        this.requestChunkSize = requestChunkSize;
    }

    public boolean isAggregateChunks() {
        return aggregateChunks;
    }

    public void setAggregateChunks(boolean aggregateChunks) {
        this.aggregateChunks = aggregateChunks;
    }

    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        if (maxQueuedRequests <= 1) {
            throw new IllegalArgumentException("MaxConnectionsPerHost must be > 1");
        }
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public int getMaxQueuedRequests() {
        return this.maxQueuedRequests;
    }

    public void setMaxQueuedRequests(int maxQueuedRequests) {
        if (maxQueuedRequests <= 1) {
            throw new IllegalArgumentException("MaxQueuedRequests must be > 1");
        }
        this.maxQueuedRequests = maxQueuedRequests;
    }

    public int getConnectionTimeoutInMillis() {
        return connectionTimeoutInMillis;
    }

    public void setConnectionTimeoutInMillis(int connectionTimeoutInMillis) {
        if (connectionTimeoutInMillis < 0) {
            throw new IllegalArgumentException("ConnectionTimeoutInMillis must be >= 0 (0 means infinite)");
        }
        this.connectionTimeoutInMillis = connectionTimeoutInMillis;
    }

    public int getRequestTimeoutInMillis() {
        return requestTimeoutInMillis;
    }

    public void setRequestTimeoutInMillis(int requestTimeoutInMillis) {
        if (requestTimeoutInMillis < 0) {
            throw new IllegalArgumentException("RequestTimeoutInMillis must be >= 0 (0 means infinite)");
        }
        this.requestTimeoutInMillis = requestTimeoutInMillis;
    }

    public boolean isUseNio() {
        return useNio;
    }

    public void setUseNio(boolean useNio) {
        this.useNio = useNio;
    }

    public int getMaxIoWorkerThreads() {
        return maxIoWorkerThreads;
    }

    public void setMaxIoWorkerThreads(int maxIoWorkerThreads) {
        if (maxIoWorkerThreads <= 1) {
            throw new IllegalArgumentException("Minimum value for maxIoWorkerThreads is 1");
        }
        this.maxIoWorkerThreads = maxIoWorkerThreads;
    }

    public int getMaxEventProcessorHelperThreads() {
        return maxEventProcessorHelperThreads;
    }

    public void setMaxEventProcessorHelperThreads(int maxEventProcessorHelperThreads) {
        if (maxEventProcessorHelperThreads <= 3) {
            throw new IllegalArgumentException("Minimum value for maxEventProcessorHelperThreads is 3");
        }
        this.maxEventProcessorHelperThreads = maxEventProcessorHelperThreads;
    }

    public HostContextFactory getHostContextFactory() {
        return hostContextFactory;
    }

    public void setHostContextFactory(HostContextFactory hostContextFactory) {
        this.hostContextFactory = hostContextFactory;
    }

    public HttpConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(HttpConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public HttpRequestFutureFactory getFutureFactory() {
        return futureFactory;
    }

    public void setFutureFactory(HttpRequestFutureFactory futureFactory) {
        this.futureFactory = futureFactory;
    }

    public TimeoutManager getTimeoutManager() {
        return timeoutManager;
    }

    public void setTimeoutManager(TimeoutManager timeoutManager) {
        this.timeoutManager = timeoutManager;
    }

    public boolean isCleanupInactiveHostContexts() {
        return cleanupInactiveHostContexts;
    }

    public void setCleanupInactiveHostContexts(boolean cleanupInactiveHostContexts) {
        this.cleanupInactiveHostContexts = cleanupInactiveHostContexts;
    }

    public SslContextFactory getSslContextFactory() {
        return sslContextFactory;
    }

    public void setSslContextFactory(SslContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
    }
}
