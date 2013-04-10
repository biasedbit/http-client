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

package com.biasedbit.http.client.connection;

import com.biasedbit.http.client.HttpRequestContext;
import com.biasedbit.http.client.future.DefaultHttpRequestFuture;
import com.biasedbit.http.client.future.HttpRequestFuture;
import com.biasedbit.http.client.timeout.TimeoutController;
import com.biasedbit.http.client.timeout.TimeoutController;
import com.biasedbit.http.client.util.Utils;
import lombok.Getter;
import lombok.Setter;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Pipelining implementation of {@link HttpConnection} interface.
 * <p/>
 * This version accepts up N idempotent HTTP 1.1 operations. Should an error occur during the execution of the pipelined
 * requests, <strong>all</strong> the currently pipelined requests will fail - and will <strong>not</strong> be
 * retried.
 * <p/>
 * This version can yield faster throughput than {@link DefaultHttpConnection} implementation for bursts up to N
 * requests of idempotent HTTP 1.1 operations, since it will batch requests and receive batch responses, potentially
 * taking advantage of being able to fit several HTTP requests in the same TCP frame.
 * <p/>
 * When the limit of pipelined requests is hit, this connection presents the same behavior as {@link
 * DefaultHttpConnection} - it starts returning {@code false} on {@code isAvailable()} and immediately failing any
 * requests that are submitted while {@code isAvailable()} would return {@code false}.
 * <p/>
 * Please note that if a connection to a host is likely to have all types of HTTP operations mixed (PUT, GET, POST, etc)
 * or also requests with version 1.0, this connection will not present any advantage over the default non-pipelining
 * implementation. Since it contains more complex logic, it will potentially be a little bit slower.
 * <p/>
 * Use this implementation when you know beforehand that you'll be performing mostly idempotent HTTP 1.1 operations.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class PipeliningHttpConnection
        extends SimpleChannelUpstreamHandler
        implements HttpConnection {

    // configuration defaults -----------------------------------------------------------------------------------------

    public static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;
    public static final boolean ALLOW_POST_PIPELINING                = false;
    public static final int     MAX_REQUESTS_IN_PIPELINE             = 50;

    // properties -----------------------------------------------------------------------------------------------------

    /**
     * Causes an explicit request to disconnect a channel after the response to a non-keepalive request is received.
     * <p/>
     * Use this only if the server you're connecting to doesn't follow standards and keeps HTTP/1.0 connections open or
     * doesn't close HTTP/1.1 connections with 'Connection: close' header.
     * <p/>
     * Defaults to {@code false}.
     */
    @Getter @Setter private boolean disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;

    /**
     * Enables or disables processing of non-idempotent operations.
     * <p/>
     * Keep in mind that the RFC clearly states that non-idempotent operations SHOULD NOT be pipelined, so enable this
     * feature at your own peril.
     * <p/>
     * Defaults to {@code false}.
     */
    @Getter @Setter private boolean allowNonIdempotentPipelining = ALLOW_POST_PIPELINING;

    /**
     * Maximum number of pipelined requests active in a single connection.
     * <p/>
     * When this number is reached, the connection will start returning {@code false} on {@link #isAvailable()}.
     * <p/>
     * Defaults to 50.
     */
    @Getter @Setter private int maxRequestsInPipeline = MAX_REQUESTS_IN_PIPELINE;

    // internal vars --------------------------------------------------------------------------------------------------

    private final String                 id;
    private final String                 host;
    private final int                    port;
    private final HttpConnectionListener listener;
    private final TimeoutController      timeoutController;
    private final Executor               executor;

    private final Object                    mutex    = new Object();
    private final Queue<HttpRequestContext> requests = new LinkedList<>();

    // state management
    private HttpResponse currentResponse;
    private boolean      readingChunks;
    private boolean      discarding;
    private Channel      channel;
    private Throwable    terminate;
    private boolean      willClose;

    // constructors ---------------------------------------------------------------------------------------------------

    public PipeliningHttpConnection(String id, String host, int port, HttpConnectionListener listener,
                                    TimeoutController timeoutController, Executor executor) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.listener = listener;
        this.timeoutController = timeoutController;
        this.executor = executor;
    }

    // SimpleChannelUpstreamHandler -----------------------------------------------------------------------------------

    @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        // Sync this big block, as there is a chance that it's a complete response.
        // If it's a complete response (in other words, all the data necessary to mark the request as finished is
        // present), and it's cancelled meanwhile, synch'ing this block will guarantee that the request will
        // be marked as complete *before* being cancelled. Since DefaultHttpRequestFuture only allows 1 completion
        // event, the request will effectively be marked as complete, even though setFailure() will be called as
        // soon as this lock is released.
        // This synchronization is performed because of edge cases where the request is completing but nearly at
        // the same instant it times out, which causes another thread to issue a cancellation. With this locking in
        // place, the request will either cancel before this block executes or after - never during.
        synchronized (mutex) {
            // Terminated or no current request; discard any incoming data as there is no one to feed it to.
            if ((terminate != null) || requests.isEmpty()) return;

            if (!readingChunks) {
                HttpResponse response = (HttpResponse) e.getMessage();
                receivedResponseForRequest(response);

                // Chunked flag is set *even if chunk agreggation occurs*. When chunk aggregation occurs, the content
                // of the message will be present and flag will still be true so all conditions must be checked!
                if (response.isChunked() &&
                    ((response.getContent() == null) || (response.getContent().readableBytes() == 0))) {
                    readingChunks = true;
                } else {
                    ChannelBuffer content = response.getContent();
                    if (content.readable()) receivedContentForRequest(content, true);

                    // Non-chunked responses are always complete.
                    responseForRequestComplete();
                }
            } else {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if (chunk.isLast()) {
                    readingChunks = false;
                    // Same considerations as above. This lock will ensure the request will either be cancelled before
                    // this lock is obtained or the request will succeed (this is the last chunk, which means it's
                    // complete).
                    receivedContentForRequest(chunk.getContent(), true);
                    responseForRequestComplete();
                } else {
                    receivedContentForRequest(chunk.getContent(), false);
                }
            }
        }
    }

    @Override public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        if (channel == null) return;

        HttpRequestContext current;
        synchronized (mutex) {
            willClose = true;
            // Apart from this method, only postResponseCleanup() removes items from the request queue.
            current = requests.poll();
        }

        if (current != null) {
            current.getFuture().setFailure(e.getCause());
            listener.requestFinished(this, current);
        }

        if (channel.isConnected()) channel.close();
    }

    @Override public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        channel = e.getChannel();
        synchronized (mutex) {
            // Testing terminate == null just in case terminate was issued meanwhile... will hardly ever happen.
            if (terminate != null) return;
        }
        listener.connectionOpened(this);
    }

    @Override public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        synchronized (mutex) {
            if (terminate == null) terminate = HttpRequestFuture.CONNECTION_LOST;
        }

        listener.connectionTerminated(this, requests);
    }

    @Override public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        // No need for any extra steps since isAvailable only turns true when channel connects.
        // Simply notify the listener that the connection failed.
        if (channel == null) listener.connectionFailed(this);
    }

    // HttpConnection -------------------------------------------------------------------------------------------------

    @Override public void terminate(Throwable reason) {
        synchronized (mutex) {
            if (terminate != null) return;

            terminate = reason;
            willClose = true;
        }

        if ((channel != null) && channel.isConnected()) channel.close();
    }

    @Override public String getId() { return id; }

    @Override public String getHost() { return host; }

    @Override public int getPort() { return port; }

    @Override public boolean isAvailable() {
        return !willClose &&
               (requests.size() < maxRequestsInPipeline) &&
               (terminate == null) &&
               (channel != null) &&
               channel.isConnected();
    }

    @Override public boolean execute(final HttpRequestContext context) {
        Utils.ensureValue(context != null, "HttpRequestContext cannot be null");

        // Test for cancellation or tampering.
        if (context.getFuture().isDone()) {
            listener.requestFinished(this, context);
            return true;
        }

        // This implementation allows multiple requests active at the same time if:
        // 1. The last submitted request is not a keep-alive request (HTTP/1.0 or Connection: close)
        // 2. Requests are non-idempotent
        // 3. Requests are non-idempotent and allowNonIdempotentPipelining is set to true

        if (!context.isIdempotent() && !allowNonIdempotentPipelining) {
            // Immediately reject non-idempotent requests.
            context.getFuture().setFailure(HttpRequestFuture.EXECUTION_REJECTED);
            listener.requestFinished(this, context);
            return true;
        }

        synchronized (mutex) {
            // If requests are performed during the period in which isAvailable() returns false, the request is
            // immediately rejected.
            if (!isAvailable()) {
                // Terminate request wasn't issued, connection is open but unavailable, so fail the request!
                context.getFuture().setFailure(HttpRequestFuture.EXECUTION_REJECTED);
                listener.requestFinished(this, context);
                return true;
            } else if ((terminate != null) || !channel.isConnected()) {
                // Terminate was issued or channel is no longer connected, don't accept execution and leave request
                // untouched.
                return false;
            }

            requests.add(context);

            // If it's a non-keep alive request mark this connection as unavailable to receive new requests.
            if (!HttpHeaders.isKeepAlive(context.getRequest())) willClose = true;
        }

        context.getFuture().markExecutionStart();

        // Now, it's theorethically possible that terminate() is called by other thread while this one is still "stuck"
        // (yes, I know write() is damn fast) on write() or the connection goes down while write() is still executing
        // and by some race-condition-miracle the events trigger faster than write() executes (hooray for imperative
        // multithreading!) thus causing write to fail. That's why this block is inside a try-catch. Y'never know...
        if (executor != null) {
            // Delegating writes to an executor results in lower throughput but also lower request/response time.
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        channel.write(context.getRequest());
                    } catch (Exception e) {
                        synchronized (mutex) {
                            // Needs to be synchronized to avoid concurrent mod exception.
                            requests.remove(context);
                        }
                        context.getFuture().setFailure(e);
                    }
                }
            });
        } else {
            // Otherwise just execute the write in the thread that called execute() (which typically is the client's
            // event dispatcher).
            try {
                channel.write(context.getRequest());
            } catch (Exception e) {
                // Some error occurred underneath, maybe ChannelClosedException or something like that.
                synchronized (mutex) {
                    // Needs to be synchronized to avoid concurrent mod exception.
                    requests.remove(context);
                }
                context.getFuture().setFailure(e);
                return true;
            }
        }

        // Attach this connection to the future, so that when it gets cancelled it can terminate this connection.
        ((DefaultHttpRequestFuture) context.getFuture()).attachConnection(this);

        // Launch a timeout checker.
        if (context.getTimeout() > 0) timeoutController.manageRequestTimeout(context);

        return true;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void receivedContentForRequest(ChannelBuffer content, boolean last) {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.
        if (discarding) return;

        HttpRequestContext request = requests.peek();
        try {
            if (last) request.getProcessor().addLastData(content);
            else request.getProcessor().addData(content);
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            request.getFuture().setFailure(currentResponse, e);
            discarding = true;
        }
    }

    @SuppressWarnings({"unchecked"})
    private void responseForRequestComplete() {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.

        // Only unlock the future if the contents weren't being discarded. If the contents were being discarded, it
        // means that receivedResponseForRequest() already triggered the future!
        if (!discarding) {
            HttpRequestContext request = requests.peek();
            request.getFuture().setSuccess(request.getProcessor().getProcessedResponse(), currentResponse);
        }

        postResponseCleanup();
    }

    /**
     * Handles the HttpReponse object received and possibly triggers the end of the current request. If the response
     * handler does not want to process the response, then the current request is finished immediately.
     *
     * @param response HttpResponse received
     */
    @SuppressWarnings({"unchecked"})
    private void receivedResponseForRequest(HttpResponse response) {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.

        currentResponse = response;
        HttpRequestContext request = requests.peek();
        try {
            if (!request.getProcessor().willProcessResponse(response)) {
                // Rather than waiting for the full content to arrive (which will be discarded), perform an early
                // trigger on the Future, signalling request is finished. Note that postResponseCleanup() is *not*
                // called in this method, which means that execution of other requests will not be allowed, even though
                // the current request has been terminated. The reason for this is that all incoming data must be safely
                // consumed (and discarded) before another request hits the network - this avoids possible response data
                // mixing.

                // Even though the processor does not want to process the response, it might still return some default
                // result, so call getProcessedResponse() on it, rather than passing null to the Future.
                request.getFuture().setSuccess(request.getProcessor().getProcessedResponse(), response);
                discarding = true;
            } else {
                // Response processor wants to process the contents of this request.
                discarding = false;
            }
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            request.getFuture().setFailure(response, e);
            discarding = true;
        }
    }

    private void postResponseCleanup() {
        // Always called inside a synchronized block.

        // Apart from this method, only exceptionCaught() removes items from the request queue.
        HttpRequestContext context = requests.poll();
        currentResponse = null;
        listener.requestFinished(this, context);

        // Close non-keep alive connections if configured to do so...
        if (disconnectIfNonKeepAliveRequest && !HttpHeaders.isKeepAlive(currentResponse)) channel.close();
    }

    // object overrides -----------------------------------------------------------------------------------------------

    @Override public String toString() {
        return new StringBuilder()
                .append("PipeliningHttpConnection{")
                .append("id='").append(id).append('\'')
                .append('(').append(host).append(':').append(port)
                .append(")}").toString();
    }
}
