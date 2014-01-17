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

import com.biasedbit.http.client.connection.*;
import com.biasedbit.http.client.event.*;
import com.biasedbit.http.client.future.DataSinkListener;
import com.biasedbit.http.client.future.RequestFuture;
import com.biasedbit.http.client.processor.DiscardProcessor;
import com.biasedbit.http.client.processor.ResponseProcessor;
import com.biasedbit.http.client.ssl.BogusSslContextFactory;
import com.biasedbit.http.client.ssl.SslContextFactory;
import com.biasedbit.http.client.timeout.HashedWheelTimeoutController;
import com.biasedbit.http.client.timeout.TimeoutController;
import com.biasedbit.http.client.util.*;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.internal.ExecutorUtil;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.biasedbit.http.client.future.RequestFuture.*;
import static com.biasedbit.http.client.util.Utils.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.*;

/**
 * Abstract implementation of the {@link HttpClient} interface. Contains most of the boilerplate code that other
 * {@link HttpClient} implementations would also need.
 * <p/>
 * This abstract implementation is in itself complete. If you extend this class, you needn't implement a single method
 * (just like {@link DefaultHttpClient} does). However you may want to override the specific behavior of one or other
 * method, rather than reimplement the whole class all over again (just like {@link StatsGatheringHttpClient} does -
 * only overrides the {@code eventHandlingLoop()} method to gather execution statistics).
 * <p/>
 * <h3>Thread safety and performance</h3> This default implementation is thread-safe and the performance does not
 * degrade when the instance is shared by multiple threads accessing it at the same time.
 * <p/>
 * <h3>Event queue (producer/consumer)</h3> When this implementation is initialised, it fires up an auxilliary thread,
 * the consumer.
 * <p/>
 * Every time one of the variants of the method {@code execute()} is called, a new
 * {@link com.biasedbit.http.client.event.ClientEvent} is generated
 * and introduced in a blocking event queue (on the caller's thread execution time). The consumer then grabs that
 * request and acts accordingly - it can either queue the request so that it is later executed in an available
 * connection, request a new connection in case no connections are available, directly execute this request, etc.
 * <p/>
 * <h3>Order</h3> By using an event queue, absolute order is guaranteed. If thread A calls {@code execute()} with a
 * request to host H prior to thread B (which also places a request for the same host), then the request provided by
 * thread A is guaranteed to be <strong>placed</strong> (i.e. written to the network) before the request placed by
 * thread B.
 * <p/>
 * This doesn't mean that request A will hit the server before request B or that the response for request A will arrive
 * before B. The reasons are obvious:
 * <p/>
 * <ul>
 * <li>A can end up in a connection slower than B's</li>
 * <li>Server can respond faster on one socket than on the other</li>
 * <li>Response for request B can have 10b and for request A 10bKb</li>
 * <li>etc</li>
 * </ul
 * <p/>
 * If you need to guarantee that a request B can only hit the server after a request A, you can either manually manage
 * that in your code through the {@link com.biasedbit.http.client.future.RequestFuture} API or configure the concrete
 * instance of this class to allow
 * at most 1 connection per host - although this last option will hurt performance globally.
 * <p/>
 * <div class="note">
 * <div class="header">Note:</div>
 * Calling {@linkplain #execute(String, int, HttpRequest, com.biasedbit.http.client.processor.ResponseProcessor) one
 * of the variants of {@code execute}}
 * with the client configured with {@linkplain #setAutoDecompress(boolean) auto-inflation} turned on will cause a
 * 'ACCEPT_ENCODING' header to be added with value 'GZIP'.
 * </div>
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHttpClient
        implements HttpClient,
                   ConnectionListener {

    // constants ------------------------------------------------------------------------------------------------------

    protected static final ClientEvent POISON = new ClientEvent() {
        @Override public EventType getEventType() { return null; }
    };

    // configuration defaults -----------------------------------------------------------------------------------------

    public static final int     CONNECTION_TIMEOUT             = 10000;
    public static final int     REQUEST_INACTIVITY_TIMEOUT     = 10000;
    public static final boolean USE_SSL                        = false;
    public static final boolean USE_NIO                        = false;
    public static final boolean AUTO_DECOMPRESS                = false;
    public static final int     MAX_CONNECTIONS_PER_HOST       = 3;
    public static final int     MAX_QUEUED_REQUESTS            = Short.MAX_VALUE;
    public static final int     MAX_IO_WORKER_THREADS          = 50;
    public static final int     MAX_HELPER_THREADS             = 20;
    public static final boolean CLEANUP_INACTIVE_HOST_CONTEXTS = true;

    // properties -----------------------------------------------------------------------------------------------------

    @Getter private int     connectionTimeout           = CONNECTION_TIMEOUT;
    @Getter private int     requestInactivityTimeout    = REQUEST_INACTIVITY_TIMEOUT;
    @Getter private boolean useNio                      = USE_NIO;
    @Getter private boolean useSsl                      = USE_SSL;
    @Getter private int     maxConnectionsPerHost       = MAX_CONNECTIONS_PER_HOST;
    @Getter private int     maxQueuedRequests           = MAX_QUEUED_REQUESTS;
    @Getter private int     maxIoWorkerThreads          = MAX_IO_WORKER_THREADS;
    @Getter private int     maxHelperThreads            = MAX_HELPER_THREADS;
    @Getter private boolean autoDecompress              = AUTO_DECOMPRESS;
    @Getter private boolean cleanupInactiveHostContexts = CLEANUP_INACTIVE_HOST_CONTEXTS;

    @Getter private ConnectionFactory connectionFactory;
    @Getter private TimeoutController timeoutController;
    @Getter private SslContextFactory sslContextFactory;

    // internal vars --------------------------------------------------------------------------------------------------

    private final Map<String, HostController> hostControllers = new HashMap<>();
    private final AtomicInteger               queuedRequests  = new AtomicInteger(0);

    private Executor                   executor;
    private ChannelFactory             channelFactory;
    private ChannelPipelineFactory     pipelineFactory;
    private ChannelGroup               channelGroup;
    private BlockingQueue<ClientEvent> eventQueue;
    private int                        connectionCounter;
    private CountDownLatch             eventConsumerLatch;
    private boolean                    internalTimeoutManager;

    private volatile boolean terminate;

    // HttpClient -----------------------------------------------------------------------------------------------------

    @Override public boolean init() {
        if (timeoutController == null) {
            timeoutController = new HashedWheelTimeoutController(); // prefer lower resources consumption over precision
            timeoutController.init();
            internalTimeoutManager = true;
        }

        if (connectionFactory == null) connectionFactory = new DefaultConnectionFactory();
        if ((sslContextFactory == null) && isHttps()) sslContextFactory = BogusSslContextFactory.getInstance();

        eventConsumerLatch = new CountDownLatch(1);
        eventQueue = new LinkedBlockingQueue<>();

        // TODO instead of fixed size thread pool, use a cached thread pool with size limit (limited growth cached pool)
        executor = Executors.newFixedThreadPool(maxHelperThreads,
                                                new NamedThreadFactory("httpHelpers"));
        Executor workerPool = Executors.newFixedThreadPool(maxIoWorkerThreads,
                                                           new NamedThreadFactory("httpWorkers"));

        if (useNio) {
            // It's only going to create 1 thread, so no harm done here.
            Executor bossPool = Executors.newCachedThreadPool();
            channelFactory = new NioClientSocketChannelFactory(bossPool, workerPool);
        } else {
            channelFactory = new OioClientSocketChannelFactory(workerPool);
        }

        channelGroup = new CleanupChannelGroup(toString());
        // Create a pipeline without the last handler (it will be added right before connecting).
        pipelineFactory = new ChannelPipelineFactory() {
            @Override public ChannelPipeline getPipeline()
                    throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                if (useSsl) {
                    SSLEngine engine = sslContextFactory.getClientContext().createSSLEngine();
                    engine.setUseClientMode(true);
                    pipeline.addLast("ssl", new SslHandler(engine));
                }

                pipeline.addLast("codec", new HttpClientCodec());
                if (autoDecompress) pipeline.addLast("decompressor", new HttpContentDecompressor());

                return pipeline;
            }
        };

        executor.execute(new Runnable() {
            @Override public void run() { eventHandlingLoop(); }
        });

        return true;
    }

    @Override public boolean isInitialized() { return (eventQueue != null) && !terminate; }

    @Override public void terminate() {
        if (terminate || eventQueue == null) return;

        // Stop accepting requests.
        terminate = true;
        // Copy any pending operations in order to signal execution request failures.
        Collection<ClientEvent> pendingEvents = new ArrayList<>(eventQueue);
        // Clear the queue and kill the consumer thread by "poisoning" the event queue.
        eventQueue.clear();
        eventQueue.add(POISON);
        try {
            eventConsumerLatch.await();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Fail all requests that were still in the event queue.
        for (ClientEvent event : pendingEvents) {
            switch (event.getEventType()) {
                case EXECUTE_REQUEST:
                    ExecuteRequestEvent executeEvent = (ExecuteRequestEvent) event;
                    executeEvent.getContext().getFuture().failedWithCause(SHUTTING_DOWN);
                    break;

                case CONNECTION_CLOSED:
                    ConnectionClosedEvent closedEvent = (ConnectionClosedEvent) event;
                    if (closedEvent.hasRequestsToRetry()) {
                        for (RequestContext context : closedEvent.getRetryRequests()) {
                            context.getFuture().failedWithCause(SHUTTING_DOWN);
                        }
                    }
                    break;
            }
        }

        // Kill all connections (will cause failure on requests executing in those connections)
        // and fail context-queued requests.
        for (HostController controller : hostControllers.values()) controller.shutdown(SHUTTING_DOWN);
        hostControllers.clear();

        try {
            channelGroup.close().await(1000);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        channelFactory.releaseExternalResources();
        if (executor != null) ExecutorUtil.terminate(executor);

        if (internalTimeoutManager) timeoutController.terminate();
    }

    @Override public RequestFuture<Object> execute(String host, int port, HttpRequest request)
            throws CannotExecuteRequestException {
        return executeWithDataSink(host, port, requestInactivityTimeout, request, DiscardProcessor.getInstance(), null);
    }

    @Override public <T> RequestFuture<T> execute(String host, int port, HttpRequest request,
                                                  ResponseProcessor<T> processor)
            throws CannotExecuteRequestException {
        return executeWithDataSink(host, port, requestInactivityTimeout, request, processor, null);
    }

    @Override public <T> RequestFuture<T> execute(String host, int port, int timeout, HttpRequest request,
                                                  ResponseProcessor<T> processor)
            throws CannotExecuteRequestException {
        return executeWithDataSink(host, port, timeout, request, processor, null);
    }

    @Override public <T> RequestFuture<T> executeWithDataSink(String host, int port, int timeout, HttpRequest request,
                                                              ResponseProcessor<T> processor,
                                                              DataSinkListener dataSinkListener)
            throws CannotExecuteRequestException {

        if (terminate) throw new CannotExecuteRequestException("HttpClient already terminated");
        if (eventQueue == null) throw new CannotExecuteRequestException("HttpClient was not initialised");

        if (dataSinkListener != null) {
            // Requests with data sink listener must be POST, PUT or PATCH (contain body)
            ensureValue((request.getMethod() == HttpMethod.POST) ||
                        (request.getMethod() == HttpMethod.PUT) ||
                        (request.getMethod() == HttpMethod.PATCH),
                        "Requests with DataSink must be POST, PUT or PATCH");
        }

        if (queuedRequests.incrementAndGet() > maxQueuedRequests) {
            queuedRequests.decrementAndGet();
            throw new CannotExecuteRequestException("Request queue is full");
        }

        // Perform these checks on the caller thread's time rather than the event dispatcher's.
        if (autoDecompress) setHeader(request, Names.ACCEPT_ENCODING, Values.GZIP);

        RequestContext<T> context = new RequestContext<>(host, port, timeout, request, processor);
        context.setDataSinkListener(dataSinkListener);

        if (!eventQueue.offer(new ExecuteRequestEvent(context))) {
            throw new CannotExecuteRequestException("Could not add request to queue");
        }

        return context.getFuture();
    }

    @Override public boolean isHttps() { return useSsl; }

    // ConnectionListener -----------------------------------------------------------------------------------------

    @Override public void connectionOpened(Connection connection) {
        if (terminate) return;

        eventQueue.offer(new ConnectionOpenEvent(connection));
    }

    @Override public void connectionTerminated(Connection connection, Collection<RequestContext> retryRequests) {
        if (terminate) {
            if ((retryRequests != null) && !retryRequests.isEmpty()) {
                for (RequestContext request : retryRequests) request.getFuture().failedWithCause(SHUTTING_DOWN);
            }
        } else {
            eventQueue.offer(new ConnectionClosedEvent(connection, retryRequests));
        }
    }

    @Override public void connectionTerminated(Connection connection) {
        if (terminate) return;

        eventQueue.offer(new ConnectionClosedEvent(connection, null));
    }

    @Override public void connectionFailed(Connection connection) {
        if (terminate) return;

        eventQueue.offer(new ConnectionFailedEvent(connection));
    }

    @Override public void requestFinished(Connection connection, RequestContext context) {
        if (terminate) return;

        eventQueue.offer(new RequestCompleteEvent(context));
    }

    // protected helpers ----------------------------------------------------------------------------------------------

    protected ClientEvent popNextEvent()
            throws InterruptedException {
        return eventQueue.take();
    }

    protected void eventQueuePoisoned() { eventConsumerLatch.countDown(); }

    protected void eventHandlingLoop() {
        while (true) {
            try {
                ClientEvent event = popNextEvent();
                if (event == POISON) {
                    eventQueuePoisoned();
                    return;
                }

                switch (event.getEventType()) {
                    case EXECUTE_REQUEST:
                        handleExecuteRequest((ExecuteRequestEvent) event);
                        break;
                    case REQUEST_COMPLETE:
                        handleRequestComplete((RequestCompleteEvent) event);
                        break;
                    case CONNECTION_OPEN:
                        handleConnectionOpen((ConnectionOpenEvent) event);
                        break;
                    case CONNECTION_CLOSED:
                        handleConnectionClosed((ConnectionClosedEvent) event);
                        break;
                    case CONNECTION_FAILED:
                        handleConnectionFailed((ConnectionFailedEvent) event);
                        break;
                    default: // Consume and do nothing, unknown event.
                }
            } catch (InterruptedException ignored) { /* poisoning the queue is the only way to stop this loop */ }
        }
    }

    // protected helpers ----------------------------------------------------------------------------------------------

    protected void handleExecuteRequest(ExecuteRequestEvent event) {
        // First, add it to the queue (or create a queue for given host if one does not exist)
        String id = hostId(event.getContext());
        HostController context = hostControllers.get(id);
        if (context == null) {
            context = HostController.createContextForRequest(event.getContext(), maxConnectionsPerHost);
            hostControllers.put(id, context);
        }

        context.addToQueue(event.getContext());
        drainQueueAndProcessResult(context);
    }

    protected void handleRequestComplete(RequestCompleteEvent event) {
        queuedRequests.decrementAndGet();

        HostController controller = hostControllers.get(hostId(event.getContext()));
        if (controller == null) return; // Can only happen if controller is cleaned meanwhile; just ignore.

        drainQueueAndProcessResult(controller);
    }

    protected void handleConnectionOpen(ConnectionOpenEvent event) {
        String id = hostId(event.getConnection());
        HostController controller = hostControllers.get(id);
        controller.connectionOpen(event.getConnection());
        // Rather than go through the whole process of drainQueue(), simply poll a single element from the head of
        // the queue into this connection (a newly opened connection is ALWAYS available).
        RequestContext nextRequest = controller.pollQueue();

        if (nextRequest != null) event.getConnection().execute(nextRequest);
    }

    protected void handleConnectionClosed(ConnectionClosedEvent event) {
        // Update the list of available connections for the same host:port.
        String id = hostId(event.getConnection());
        HostController controller = hostControllers.get(id);
        controller.connectionClosed(event.getConnection());

        // Restore the requests to the queue prior to cleanup check - avoids unnecessary cleanup when there are request
        // to be retried.
        if (event.hasRequestsToRetry()) controller.restoreRequestsToQueue(event.getRetryRequests());

        // If the pool has no connections and no requests are in queue for this host, then clean it up.
        // No requests in queue, no connections open or opening... Cleanup resources.
        if (controller.isCleanable() && cleanupInactiveHostContexts) hostControllers.remove(id);

        drainQueueAndProcessResult(controller);
    }

    protected void handleConnectionFailed(ConnectionFailedEvent event) {
        // Update the list of available connections for the same host:port.
        String id = hostId(event.getConnection());
        HostController controller = hostControllers.get(id);
        controller.connectionFailed();

        // If there are no more connections active or establishing we need to fail all queued requests.
        if (!controller.hasConnections()) controller.failAllRequests(CANNOT_CONNECT);
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void drainQueueAndProcessResult(HostController controller) {
        HostController.DrainQueueResult result = controller.drainQueue();
        switch (result) {
            case OPEN_CONNECTION:
                openConnection(controller);
                break;

            case QUEUE_EMPTY:
            case NOT_DRAINED:
            case DRAINED:
            default:
        }
    }

    private String hostId(Connection connection) { return hostId(connection.getHost(), connection.getPort()); }

    private String hostId(RequestContext context) { return hostId(context.getHost(), context.getPort()); }

    private String hostId(HostController context) { return hostId(context.getHost(), context.getPort()); }

    private String hostId(String host, int port) { return string(host, ":", port); }

    private void openConnection(final HostController controller) {
        // No need to recheck whether a connection can be opened or not, that was done already inside the HttpContext.

        // Try to create a pipeline before signalling a new connection is being open.
        // This should never throw exceptions but who knows...
        final ChannelPipeline pipeline = createChannelPipeline();

        // Signal that a new connection is opening.
        controller.connectionOpening();

        // server:port-X
        String id = new StringBuilder().append(hostId(controller)).append("-").append(connectionCounter++).toString();

        // If not using NIO, then delegate the blocking write() call to the executor.
        Executor writeDelegator = useNio ? null : executor;

        final Connection connection = connectionFactory
                .createConnection(id, controller.getHost(), controller.getPort(),
                                  this, timeoutController, writeDelegator);

        pipeline.addLast("handler", connection);

        // Delegate actual connection to other thread, since calling connect is a blocking call.
        executor.execute(new Runnable() {
            @Override public void run() {
                ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
                bootstrap.setOption("reuseAddress", true);
                bootstrap.setOption("connectTimeoutMillis", connectionTimeout);
                bootstrap.setPipeline(pipeline);

                InetSocketAddress address = new InetSocketAddress(controller.getHost(), controller.getPort());
                ChannelFuture future = bootstrap.connect(address);
                future.addListener(new ChannelFutureListener() {
                    @Override public void operationComplete(ChannelFuture future)
                            throws Exception {
                        if (future.isSuccess()) channelGroup.add(future.getChannel());
                    }
                });
            }
        });
    }


    @SneakyThrows(Exception.class) // IDE still reports error, but it's OK...
    private ChannelPipeline createChannelPipeline() { return pipelineFactory.getPipeline(); }

    // getters & setters ----------------------------------------------------------------------------------------------

    /**
     * Sets the connection to host timeout, in milliseconds.
     * <p/>
     * Defaults to 2000.
     *
     * @param connectionTimeoutInMillis Connection to host timeout, in milliseconds.
     */
    public void setConnectionTimeout(int connectionTimeoutInMillis) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");
        ensureValue(connectionTimeoutInMillis >= 0, "connectionTimeoutInMillis must be >= 0 (0 means infinite)");

        connectionTimeout = connectionTimeoutInMillis;
    }

    /**
     * Sets the default request timeout, in milliseconds.
     * <p/>
     * When {@link #execute(String, int, HttpRequest, com.biasedbit.http.client.processor.ResponseProcessor)} is
     * called (i.e. the variant without explicit request timeout) then this value is applied as the request timeout.
     * <p/>
     * Requests whose execution time exceeds (precision depends on the
     * {@link com.biasedbit.http.client.timeout.TimeoutController} chosen) this value will be considered failed and
     * their {@link com.biasedbit.http.client.future.RequestFuture} will be released with cause
     * {@link com.biasedbit.http.client.future.RequestFuture#TIMED_OUT}.
     * <p/>
     * Defaults to 2000.
     *
     * @param requestInactivityTimeoutInMillis Default request timeout, in milliseconds.
     */
    public void setRequestInactivityTimeout(int requestInactivityTimeoutInMillis) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");
        ensureValue(requestInactivityTimeoutInMillis >= 0,
                    "requestInactivityTimeoutInMillis must be >= 0 (0 means infinite)");

        requestInactivityTimeout = requestInactivityTimeoutInMillis;
    }

    /**
     * Whether this client should use non-blocking IO (New I/O or NIO) or blocking IO (Plain Socket Old IO or OIO).
     * <p/>
     * NIO is generally better for higher throughput (scenarios with an elevated number of open connections) while OIO
     * is always better for latency (and scenarios where a low number of connections is open).
     * <p/>
     * If the number of connections open is not supposed to exceed 10~20, then use OIO as it typically presents better
     * results.
     * <p/>
     * Since the writes in OIO are blocking, the HTTP connections will delegate the call to
     * {@link org.jboss.netty.channel.Channel#write(Object)} to an executor (provided by this {@link HttpClient}).
     * <p/>
     * Defaults to {@code true}.
     *
     * @param useNio {@code true} if this client should use NIO, {@code false} if it should use OIO.
     */
    public void setUseNio(boolean useNio) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");

        this.useNio = useNio;
    }

    /**
     * Whether this client should create SSL or non-SSL connections.
     * <p/>
     * All connections are affected by this flag.
     * <p/>
     * Defaults to {@code false}.
     *
     * @param useSsl {@code true} if all connections will have SSL support, {@code false} otherwise.
     */
    public void setUseSsl(boolean useSsl) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");

        this.useSsl = useSsl;
    }

    /**
     * Sets the maximum number of active connections per host.
     * <p/>
     * This number also limits the number of connections being established so that
     * {@code connectionsOpen + connectionsOpening <= maxConnectionsPerHost} is always true.
     * <p/>
     * Defaults to 3.
     *
     * @param maxConnectionsPerHost Maximum number of total active connections (open + opening) per host at a given
     *                              time. Minimum value is 1.
     */
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");
        ensureValue(maxConnectionsPerHost >= 1, "maxConnectionsPerHost must be >= 1");

        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    /**
     * Sets the maximum number of queued requests for this client.
     * <p/>
     * If the number of queued requests is exceeded, calling
     * {@linkplain #execute(String, int, HttpRequest, com.biasedbit.http.client.processor.ResponseProcessor) one of
     * the variants of {@code execute()}} will throw a {@link CannotExecuteRequestException}.
     * <p/>
     * Defaults to {@link Short#MAX_VALUE}.
     *
     * @param maxQueuedRequests Maximum number of queued requests at any given moment.
     */
    public void setMaxQueuedRequests(int maxQueuedRequests) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");
        ensureValue(maxQueuedRequests > 1, "maxQueuedRequests must be > 1");

        this.maxQueuedRequests = maxQueuedRequests;
    }

    /**
     * Maximum number of worker threads for the executor provided to Netty's {@link ChannelFactory}.
     * <p/>
     * Defaults to 50.
     *
     * @param maxIoWorkerThreads Maximum number of IO worker threads.
     */
    public void setMaxIoWorkerThreads(int maxIoWorkerThreads) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");
        ensureValue(maxIoWorkerThreads > 1, "maxIoWorkerThreads must be > 1");

        this.maxIoWorkerThreads = maxIoWorkerThreads;
    }

    /**
     * Maximum number of helper threads for the event processor.
     * <p/>
     * There are tasks performed by the internal event processor that are blocking and/or slow and need not be executed
     * in serial mode. Therefore the event processor delegates them to helper threads in order to keep doing what it's
     * supposed to do: consume events from the event queue.
     * <p/>
     * Defaults to 20.
     *
     * @param maxHelperThreads Maximum number of IO worker threads.
     */
    public void setMaxHelperThreads(int maxHelperThreads) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");
        ensureValue(maxHelperThreads > 3, "maxHelperThreads must be > 3");

        this.maxHelperThreads = maxHelperThreads;
    }

    /**
     * Whether responses should be auto inflated (decompressed) or not.
     * <p/>
     * Setting this flag to true will cause a 'Accept-Encoding' header with value 'gzip' to be added to the requests
     * submitted.
     * <p/>
     * Defaults to {@code true}.
     *
     * @param autoDecompress {@code true} if the connections should automatically decompress gzip content,
     * {@code false} otherwise.
     */
    public void setAutoDecompress(boolean autoDecompress) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");

        this.autoDecompress = autoDecompress;
    }

    /**
     * Whether empty {@link HostController}s should be immediately cleaned up.
     * <p/>
     * When a {@linkplain HostController host controller} has no more queued requests nor active connections nor
     * connections opening, it is eligible for cleanup. Setting this flag to {@code true} will cause them to be
     * instantly reaped when such conditions are met.
     * <p/>
     * Unless your client will be performing requests to many different host/port combinations, you should set this flag
     * to {@code false}. While the overhead of creating/cleaning these contexts is minimal, it can be avoided in these
     * scenarios.
     * <p/>
     * Defaults to {@code true}.
     *
     * @param cleanupInactiveHostContexts {@code true} if inactive host contexts should be cleaned up, {@code false}
     *                                    otherwise.
     * @see com.biasedbit.http.client.util.HostController
     */
    public void setCleanupInactiveHostContexts(boolean cleanupInactiveHostContexts) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");

        this.cleanupInactiveHostContexts = cleanupInactiveHostContexts;
    }

    /**
     * The {@link com.biasedbit.http.client.connection.ConnectionFactory} that will be used to create new
     * {@link com.biasedbit.http.client.connection.Connection}.
     * <p/>
     * Defaults to {@link com.biasedbit.http.client.connection.DefaultConnectionFactory} if none is provided.
     *
     * @param connectionFactory The {@link com.biasedbit.http.client.connection.ConnectionFactory} to be used.
     * @see com.biasedbit.http.client.connection.ConnectionFactory
     * @see com.biasedbit.http.client.connection.Connection
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");

        this.connectionFactory = connectionFactory;
    }

    /**
     * The {@link com.biasedbit.http.client.timeout.TimeoutController} that will be used to check request timeouts.
     * <p/>
     * If no instance is provided, a new instance is created upon calling {@link #init()}. This instance will be
     * automatically terminated when {@link #terminate()} is called.
     * <p/>
     * If an external {@link com.biasedbit.http.client.timeout.TimeoutController} is provided, then it must be
     * pre-initialised (i.e. its {@link com.biasedbit.http.client.timeout.TimeoutController#init()} must be called and
     * return {@code true}) and it must be post-terminated (i.e. its
     * {@link com.biasedbit.http.client.timeout.TimeoutController#terminate()} must be called after this instance
     * of {@link HttpClient} is disposed).
     * <p/>
     * Defaults to a new instance of {@link com.biasedbit.http.client.timeout.HashedWheelTimeoutController}.
     *
     * @param timeoutController The {@link com.biasedbit.http.client.timeout.TimeoutController} instance to use.
     * @see com.biasedbit.http.client.timeout.TimeoutController
     */
    public void setTimeoutController(TimeoutController timeoutController) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");

        this.timeoutController = timeoutController;
    }

    public void setSslContextFactory(SslContextFactory sslContextFactory) {
        ensureState(eventQueue == null, "Cannot modify property after initialization");

        this.sslContextFactory = sslContextFactory;
    }
}
