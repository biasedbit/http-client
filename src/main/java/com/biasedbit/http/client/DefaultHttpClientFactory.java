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

import com.biasedbit.http.client.connection.ConnectionFactory;
import com.biasedbit.http.client.ssl.SslContextFactory;
import com.biasedbit.http.client.timeout.TimeoutController;
import lombok.Getter;
import lombok.Setter;

import static com.biasedbit.http.client.DefaultHttpClient.*;

/**
 * Creates subclasses of {@link DefaultHttpClient} depending on configuration parameters.
 * <p/>
 * If this factory is configured with {@linkplain #setGatherStats(boolean) statistics gathering}, it will
 * generate instances of {@link StatsGatheringHttpClient}, otherwise it generates instances of
 * {@link DefaultHttpClient}.
 * <p/>
 * <strong>For the meaning of the configuration parameters, please take a look at {@link DefaultHttpClient}'s setter
 * methods.</strong>
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHttpClientFactory
        implements HttpClientFactory {

    // properties -----------------------------------------------------------------------------------------------------

    @Getter @Setter private boolean gatherStats = false;

    @Getter @Setter private int     connectionTimeout           = CONNECTION_TIMEOUT;
    @Getter @Setter private int     requestInactivityTimeout    = REQUEST_INACTIVITY_TIMEOUT;
    @Getter @Setter private boolean useSsl                      = USE_SSL;
    @Getter @Setter private boolean useNio                      = USE_NIO;
    @Getter @Setter private boolean autoDecompress              = AUTO_DECOMPRESS;
    @Getter @Setter private int     maxConnectionsPerHost       = MAX_CONNECTIONS_PER_HOST;
    @Getter @Setter private int     maxQueuedRequests           = MAX_QUEUED_REQUESTS;
    @Getter @Setter private int     maxIoWorkerThreads          = MAX_IO_WORKER_THREADS;
    @Getter @Setter private int     maxHelperThreads            = MAX_HELPER_THREADS;
    @Getter @Setter private boolean cleanupInactiveHostContexts = CLEANUP_INACTIVE_HOST_CONTEXTS;

    @Getter @Setter private ConnectionFactory connectionFactory;
    @Getter @Setter private TimeoutController timeoutController;
    @Getter @Setter private SslContextFactory sslContextFactory;

    // HttpClientFactory ----------------------------------------------------------------------------------------------

    @Override public HttpClient createClient() {
        DefaultHttpClient client;

        if (gatherStats) client = new StatsGatheringHttpClient();
        else client = new DefaultHttpClient();

        client.setConnectionTimeout(connectionTimeout);
        client.setRequestInactivityTimeout(requestInactivityTimeout);
        client.setUseSsl(useSsl);
        client.setUseNio(useNio);
        client.setAutoDecompress(autoDecompress);
        client.setMaxConnectionsPerHost(maxConnectionsPerHost);
        client.setMaxQueuedRequests(maxQueuedRequests);
        client.setMaxIoWorkerThreads(maxIoWorkerThreads);
        client.setMaxHelperThreads(maxHelperThreads);
        client.setCleanupInactiveHostContexts(cleanupInactiveHostContexts);

        client.setConnectionFactory(connectionFactory);
        client.setTimeoutController(timeoutController);
        client.setSslContextFactory(sslContextFactory);

        return client;
    }
}
