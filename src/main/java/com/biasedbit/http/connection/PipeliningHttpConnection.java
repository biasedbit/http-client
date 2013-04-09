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

package com.biasedbit.http.connection;

import com.biasedbit.http.HttpRequestContext;
import com.biasedbit.http.future.DefaultHttpRequestFuture;
import com.biasedbit.http.future.HttpRequestFuture;
import com.biasedbit.http.timeout.TimeoutManager;
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
public class PipeliningHttpConnection extends SimpleChannelUpstreamHandler
        implements HttpConnection {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;
    private static final boolean ALLOW_NON_IDEMPOTENT_PIPELINING      = false;
    private static final int     MAX_REQUESTS_IN_PIPELINE             = 50;

    // properties -----------------------------------------------------------------------------------------------------

    private final String                 id;
    private final String                 host;
    private final int                    port;
    private final HttpConnectionListener listener;
    private final TimeoutManager         timeoutManager;
    private final Executor               executor;
    private       boolean                disconnectIfNonKeepAliveRequest;
    private       boolean                allowNonIdempotentPipelining;
    private       int                    maxRequestsInPipeline;

    // internal vars --------------------------------------------------------------------------------------------------

    private volatile Channel                   channel;
    private volatile Throwable                 terminate;
    private          HttpResponse              currentResponse;
    private          boolean                   readingChunks;
    private volatile boolean                   willClose;
    private          boolean                   discarding;
    private final    Object                    mutex;
    private final    Queue<HttpRequestContext> requests;

    // constructors ---------------------------------------------------------------------------------------------------

    public PipeliningHttpConnection(String id, String host, int port, HttpConnectionListener listener,
                                    TimeoutManager timeoutManager) {
        this(id, host, port, listener, timeoutManager, null);
    }

    public PipeliningHttpConnection(String id, String host, int port, HttpConnectionListener listener,
                                    TimeoutManager timeoutManager, Executor executor) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.listener = listener;
        this.timeoutManager = timeoutManager;
        this.executor = executor;
        this.mutex = new Object();
        this.requests = new LinkedList<HttpRequestContext>();
        this.disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;
        this.allowNonIdempotentPipelining = ALLOW_NON_IDEMPOTENT_PIPELINING;
        this.maxRequestsInPipeline = MAX_REQUESTS_IN_PIPELINE;
    }

    // SimpleChannelUpstreamHandler -----------------------------------------------------------------------------------

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        // Synch this big block, as there is a chance that it's a complete response.
        // If it's a complete response (in other words, all the data necessary to mark the request as finished is
        // present), and it's cancelled meanwhile, synch'ing this block will guarantee that the request will
        // be marked as complete *before* being cancelled. Since DefaultHttpRequestFuture only allows 1 completion
        // event, the request will effectively be marked as complete, even though setFailure() will be called as
        // soon as this lock is released.
        // This synchronization is performed because of edge cases where the request is completing but nearly at
        // the same instant it times out, which causes another thread to issue a cancellation. With this locking in
        // place, the request will either cancel before this block executes or after - never during.
        synchronized (this.mutex) {
            if ((this.terminate != null) || this.requests.isEmpty()) {
                // Terminated or no current request; discard any incoming data as there is no one to feed it to.
                return;
            }

            if (!this.readingChunks) {
                HttpResponse response = (HttpResponse) e.getMessage();
                this.receivedResponseForRequest(response);

                // Chunked flag is set *even if chunk agreggation occurs*. When chunk aggregation occurs, the content
                // of the message will be present and flag will still be true so all conditions must be checked!
                if (response.isChunked() &&
                    ((response.getContent() == null) || (response.getContent().readableBytes() == 0))) {
                    this.readingChunks = true;
                } else {
                    ChannelBuffer content = response.getContent();
                    if (content.readable()) {
                        this.receivedContentForRequest(content, true);
                    }

                    // Non-chunked responses are always complete.
                    this.responseForRequestComplete();
                }
            } else {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if (chunk.isLast()) {
                    this.readingChunks = false;
                    // Same considerations as above. This lock will ensure the request will either be cancelled before
                    // this lock is obtained or the request will succeed (this is the last chunk, which means it's
                    // complete).
                    this.receivedContentForRequest(chunk.getContent(), true);
                    this.responseForRequestComplete();
                } else {
                    this.receivedContentForRequest(chunk.getContent(), false);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (this.channel == null) {
            return;
        }

        HttpRequestContext current;
        synchronized (this.mutex) {
            this.willClose = true;
            // Apart from this method, only postResponseCleanup() removes items from the request queue.
            current = this.requests.poll();
        }

        if (current != null) {
            current.getFuture().setFailure(e.getCause());
            this.listener.requestFinished(this, current);
        }

        if (this.channel.isConnected()) {
            this.channel.close();
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        this.channel = e.getChannel();
        synchronized (this.mutex) {
            // Testing terminate == null just in case terminate was issued meanwhile... will hardly ever happen.
            if (this.terminate != null) {
                return;
            }
        }
        this.listener.connectionOpened(this);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        synchronized (this.mutex) {
            if (this.terminate == null) {
                this.terminate = HttpRequestFuture.CONNECTION_LOST;
            }
        }

        this.listener.connectionTerminated(this, this.requests);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (this.channel == null) {
            // No need for any extra steps since isAvailable only turns true when channel connects.
            // Simply notify the listener that the connection failed.
            this.listener.connectionFailed(this);
        }
    }

    // HttpConnection -------------------------------------------------------------------------------------------------

    @Override
    public void terminate(Throwable reason) {
        synchronized (this.mutex) {
            if (this.terminate != null) {
                return;
            }
            this.terminate = reason;
            this.willClose = true;
        }

        if ((this.channel != null) && this.channel.isConnected()) {
            this.channel.close();
        }
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public boolean isAvailable() {
        return !this.willClose &&
               (this.requests.size() < this.maxRequestsInPipeline) &&
               (this.terminate == null) &&
               (this.channel != null) &&
               this.channel.isConnected();
    }

    @Override
    public boolean execute(final HttpRequestContext context) {
        if (context == null) {
            throw new IllegalArgumentException("HttpRequestContext cannot be null");
        }

        // Test for cancellation or tampering.
        if (context.getFuture().isDone()) {
            this.listener.requestFinished(this, context);
            return true;
        }

        // This implementation allows multiple requests active at the same time if:
        // 1. The last submitted request is not a keep-alive request (HTTP/1.0 or Connection: close)
        // 2. Requests are non-idempotent
        // 3. Requests are non-idempotent and allowNonIdempotentPipelining is set to true

        if (!context.isIdempotent() && !this.allowNonIdempotentPipelining) {
            // Immediately reject non-idempotent requests.
            context.getFuture().setFailure(HttpRequestFuture.EXECUTION_REJECTED);
            this.listener.requestFinished(this, context);
            return true;
        }

        synchronized (this.mutex) {
            // If requests are performed during the period in which isAvailable() returns false, the request is
            // immediately rejected.
            if (!this.isAvailable()) {
                // Terminate request wasn't issued, connection is open but unavailable, so fail the request!
                context.getFuture().setFailure(HttpRequestFuture.EXECUTION_REJECTED);
                this.listener.requestFinished(this, context);
                return true;
            } else if ((this.terminate != null) || !this.channel.isConnected()) {
                // Terminate was issued or channel is no longer connected, don't accept execution and leave request
                // untouched.
                return false;
            }

            this.requests.add(context);
            if (!HttpHeaders.isKeepAlive(context.getRequest())) {
                // Non-keep alive request, so mark this connection as unavailable to receive new requests.
                this.willClose = true;
            }
        }

        context.getFuture().markExecutionStart();

        // Now, it's theorethically possible that terminate() is called by other thread while this one is still "stuck"
        // (yes, I know write() is damn fast) on write() or the connection goes down while write() is still executing
        // and by some race-condition-miracle the events trigger faster than write() executes (hooray for imperative
        // multithreading!) thus causing write to fail. That's why this block is inside a try-catch. Y'never know...
        if (this.executor != null) {
            // Delegating writes to an executor results in lower throughput but also lower request/response time.
            this.executor.execute(new Runnable() {
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
                this.channel.write(context.getRequest());
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
        if (context.getTimeout() > 0) {
            this.timeoutManager.manageRequestTimeout(context);
        }

        return true;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void receivedContentForRequest(ChannelBuffer content, boolean last) {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.
        if (this.discarding) {
            return;
        }

        HttpRequestContext request = this.requests.peek();
        try {
            if (last) {
                request.getProcessor().addLastData(content);
            } else {
                request.getProcessor().addData(content);
            }
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            request.getFuture().setFailure(this.currentResponse, e);
            this.discarding = true;
        }
    }

    @SuppressWarnings({"unchecked"})
    private void responseForRequestComplete() {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.

        // Only unlock the future if the contents weren't being discarded. If the contents were being discarded, it
        // means that receivedResponseForRequest() already triggered the future!
        if (!this.discarding) {
            HttpRequestContext request = this.requests.peek();
            request.getFuture().setSuccess(request.getProcessor().getProcessedResponse(), this.currentResponse);
        }

        this.postResponseCleanup();
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

        this.currentResponse = response;
        HttpRequestContext request = this.requests.peek();
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
                this.discarding = true;
            } else {
                // Response processor wants to process the contents of this request.
                this.discarding = false;
            }
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            request.getFuture().setFailure(response, e);
            this.discarding = true;
        }
    }

    private void postResponseCleanup() {
        // Always called inside a synchronized block.

        // Apart from this method, only exceptionCaught() removes items from the request queue.
        HttpRequestContext context = this.requests.poll();
        this.currentResponse = null;
        this.listener.requestFinished(this, context);

        // Close non-keep alive connections if configured to do so...
        if (this.disconnectIfNonKeepAliveRequest && !HttpHeaders.isKeepAlive(this.currentResponse)) {
            this.channel.close();
        }
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isDisconnectIfNonKeepAliveRequest() {
        return disconnectIfNonKeepAliveRequest;
    }

    /**
     * Causes an explicit request to disconnect a channel after the response to a non-keepalive request is received.
     * <p/>
     * Use this only if the server you're connecting to doesn't follow standards and keeps HTTP/1.0 connections open or
     * doesn't close HTTP/1.1 connections with 'Connection: close' header.
     *
     * @param disconnectIfNonKeepAliveRequest
     *         Whether this {@code HttpConnection} should explicitly disconnect after executing a non-keepalive
     *         request.
     */
    public void setDisconnectIfNonKeepAliveRequest(boolean disconnectIfNonKeepAliveRequest) {
        this.disconnectIfNonKeepAliveRequest = disconnectIfNonKeepAliveRequest;
    }

    public boolean isAllowNonIdempotentPipelining() {
        return allowNonIdempotentPipelining;
    }

    /**
     * Enables or disables processing of non-idempotent operations.
     * <p/>
     * Keep in mind that the RFC clearly states that non-idempotent operations SHOULD NOT be pipelined, so enable this
     * feature at your own peril.
     *
     * @param allowNonIdempotentPipelining Whether this {@link HttpConnection} should accept non-idempotent operations.
     */
    public void setAllowNonIdempotentPipelining(boolean allowNonIdempotentPipelining) {
        this.allowNonIdempotentPipelining = allowNonIdempotentPipelining;
    }

    public int getMaxRequestsInPipeline() {
        return maxRequestsInPipeline;
    }

    /**
     * Changes the number of maximum requests active in a single connection.
     * <p/>
     * When this number is reached, the connection will start returning {@code false} on {@link #isAvailable()}.
     *
     * @param maxRequestsInPipeline Number of maximum pipelined requests.
     */
    public void setMaxRequestsInPipeline(int maxRequestsInPipeline) {
        this.maxRequestsInPipeline = maxRequestsInPipeline;
    }

    // object overrides -----------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("PipeliningHttpConnection{")
                .append("id='").append(this.id).append('\'')
                .append('(').append(this.host).append(':').append(this.port)
                .append(")}").toString();
    }
}
