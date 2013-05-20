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

import com.biasedbit.http.client.future.DataSinkListener;
import com.biasedbit.http.client.future.RequestFuture;
import com.biasedbit.http.client.processor.ResponseProcessor;
import com.biasedbit.http.client.timeout.TimeoutController;
import com.biasedbit.http.client.util.RequestContext;
import com.biasedbit.http.client.util.Utils;
import lombok.*;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.util.Arrays;
import java.util.concurrent.Executor;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;

/**
 * Non-pipelining implementation of {@link Connection} interface.
 * <p/>
 * Since some server implementations aren't fully <a href="http://tools.ietf.org/html/rfc2616">RFC 2616</a> compliant
 * when it comes to pipelining, this is the default implementation.
 * <p/>
 * If you are sure the servers you will be connecting to support pipelining correctly, you can use the {@linkplain
 * PipeliningConnection pipelining version}.
 * <p/>
 * This implementation only accepts one request at a time. If {@link
 * #execute(com.biasedbit.http.client.util.RequestContext) execute()} is called while {@link #isAvailable()} would
 * return false, the request will be accepted and immediately fail with
 * {@link com.biasedbit.http.client.future.RequestFuture#EXECUTION_REJECTED} <strong>unless</strong> the socket has
 * been disconnected (in which case the request will not fail but
 * {@link #execute(com.biasedbit.http.client.util.RequestContext)} will return {@code false} instead).
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@RequiredArgsConstructor
@ToString(of = {"id", "host", "port"})
public class DefaultConnection
        extends SimpleChannelUpstreamHandler
        implements Connection,
                   DataSink {

    // configuration defaults -----------------------------------------------------------------------------------------

    public static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;
    public static final boolean RESTORE_NON_IDEMPOTENT_OPERATIONS    = false;

    // properties -----------------------------------------------------------------------------------------------------

    /**
     * Causes an explicit request to disconnect a channel after the response to a non-keepalive request is received.
     * <p/>
     * Use this only if the server you're connecting to doesn't follow standards and keeps HTTP/1.0 connections open or
     * doesn't close HTTP/1.1 connections with 'Connection: close' header.
     */
    @Getter @Setter private boolean disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;
    /**
     * Explicitly enables or disables recovery of non-idempotent operations when connections go down.
     * <p/>
     * When a connection goes down while executing a request it can restore that request by sending it back to the
     * listener inside the {@link ConnectionListener#connectionTerminated(Connection, java.util.Collection)}
     * call.
     * <p/>
     * This can be dangerous for non-idempotent operations, because there is no guarantee that the request reached the
     * server and executed.
     * <p/>
     * By default, this option is disabled (safer).
     */
    @Getter @Setter private boolean restoreNonIdempotentOperations  = RESTORE_NON_IDEMPOTENT_OPERATIONS;

    // internal vars --------------------------------------------------------------------------------------------------

    private final String             id;
    private final String             host;
    private final int                port;
    private final ConnectionListener listener;
    private final TimeoutController  timeoutController;
    private final Executor           executor;

    private final Object mutex = new Object();

    private Channel channel;
    private boolean available;

    // state management
    private boolean        readingChunks;
    private RequestContext currentRequest;
    private HttpResponse   currentResponse;
    private boolean        discarding;
    private boolean        continueReceived;

    private volatile Throwable terminate;

    // SimpleChannelUpstreamHandler -----------------------------------------------------------------------------------

    @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        // Synch this big block, as there is a chance that it's a complete response.
        // If it's a complete response (in other words, all the data necessary to mark the request as finished is
        // present), and it's cancelled meanwhile, synch'ing this block will guarantee that the request will
        // be marked as complete *before* being cancelled. Since DefaultRequestFuture only allows 1 completion
        // event, the request will effectively be marked as complete, even though failedWithCause() will be called as
        // soon as this lock is released.
        // This synchronization is performed because of edge cases where the request is completing but nearly at
        // the same instant it times out, which causes another thread to issue a cancellation. With this locking in
        // place, the request will either cancel before this block executes or after - never during.
        synchronized (mutex) {
            // When terminated or no current request, discard any incoming data as there is no one to feed it to.
            if ((terminate != null) || (currentRequest == null)) return;

            if (!readingChunks) {
                HttpResponse response = (HttpResponse) e.getMessage();

                // Ignore CONTINUE responses, unless we have a data sink listener
                if (HttpResponseStatus.CONTINUE.equals(response.getStatus())) {
                    continueReceived = true;
                    DataSinkListener sinkListener = currentRequest.getDataSinkListener();
                    if (sinkListener != null) sinkListener.readyToSendData(this);

                    return;
                }

                receivedResponseForCurrentRequest(response);

                // Chunked flag is set *even if chunk agreggation occurs*. When chunk aggregation occurs, the content
                // of the message will be present and flag will still be true so all conditions must be checked!
                if (response.isChunked() &&
                    ((response.getContent() == null) || (response.getContent().readableBytes() == 0))) {
                    readingChunks = true;
                } else {
                    ChannelBuffer content = response.getContent();
                    if (content.readable()) receivedContentForCurrentRequest(content, true);

                    // Non-chunked responses are always complete.
                    responseForCurrentRequestComplete();
                }
            } else {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if (chunk.isLast()) {
                    readingChunks = false;
                    // Same considerations as above. This lock will ensure the request will either be cancelled before
                    // this lock is obtained or the request will succeed (this is the last chunk, which means it's
                    // complete).
                    receivedContentForCurrentRequest(chunk.getContent(), true);
                    responseForCurrentRequestComplete();
                } else {
                    receivedContentForCurrentRequest(chunk.getContent(), false);
                }
            }
        }
    }

    @Override public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        channel = e.getChannel();
        synchronized (mutex) {
            // Just in case terminate was issued meanwhile... will hardly ever happen.
            if (terminate != null) return;

            available = true;
        }
        listener.connectionOpened(this);
    }

    @Override public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        terminate(e.getCause(), true);
    }

    @Override public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        terminate(RequestFuture.CONNECTION_LOST, true);
    }

    @Override public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception {
        if ((currentRequest == null) || !continueReceived) return;

        DataSinkListener sinkListener = currentRequest.getDataSinkListener();
        if (sinkListener != null) sinkListener.writeComplete(this, e.getWrittenAmount());
    }

    // Connection -----------------------------------------------------------------------------------------------------

    @Override public void terminate(Throwable reason) { terminate(reason, false); }

    @Override public String getId() { return id; }

    @Override public String getHost() { return host; }

    @Override public int getPort() { return port; }

    @Override public boolean isAvailable() { return available; }

    @Override public boolean execute(final RequestContext context) {
        Utils.ensureValue(context != null, "RequestContext cannot be null");

        // Test for cancellation.
        if (context.getFuture().isDone()) {
            listener.requestFinished(this, context);
            return true;
        }

        synchronized (mutex) {
            boolean channelOpenAndReady = (channel != null) && channel.isConnected();

            // This implementation only allows one execution at a time. If requests are performed during the period in
            // which isAvailable() returns false, the request is immediately rejected.
            if (!available && (terminate == null) && channelOpenAndReady) {
                // Terminate request wasn't issued, connection is open but unavailable, so fail the request!
                context.getFuture().failedWithCause(RequestFuture.EXECUTION_REJECTED);
                listener.requestFinished(this, context);
                return true;
            } else if ((terminate != null) || !channelOpenAndReady) {
                // Terminate was issued or channel is no longer connected, don't accept execution and leave request
                // untouched. Switching available to false isn't really necessary but doesn't harm either.
                available = false;
                return false;
            }

            // Mark connection as unavailable.
            available = false;
        }

        currentRequest = context;
        context.getFuture().markExecutionStart();

        // Now, it's theorethically possible that terminate() is called by other thread while this one is still "stuck"
        // (yes, I know write() is damn fast) on write() or the connection goes down while write() is still executing
        // and by some race-condition-oddity the events trigger faster than write() executes thus causing write to fail.
        // Hence this block inside a try-catch.
        if (executor != null) {
            // Delegating writes to an executor results in lower throughput but also lower request/response time.
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        channel.write(context.getRequest());
                    } catch (Exception e) {
                        handleWriteFailed(e);
                    }
                }
            });
        } else {
            // Otherwise just execute the write in the thread that called execute() (which typically is the client's
            // event dispatcher).
            try {
                channel.write(context.getRequest());
            } catch (Exception e) {
                // Some error occurred underneath, maybe ChannelClosedException.
                handleWriteFailed(e);
                return true;
            }
        }

        // Attach this connection to the future, so that when it gets cancelled it can terminate this connection.
        context.getFuture().attachConnection(this);

        // Launch a timeout checker.
        if (context.getTimeout() > 0) timeoutController.controlTimeout(context);

        return true;
    }

    // DataSink -------------------------------------------------------------------------------------------------------

    @Override public boolean isConnected() { return (terminate == null) && (channel != null) && channel.isConnected(); }

    @Override public void disconnect() { terminate(RequestFuture.CANCELLED, false); }

    @Override public void sendData(ChannelBuffer data, boolean isLast) {
        if (!isConnected() || (currentRequest == null) || !continueReceived) return;

        if ((data != null) && (data.readableBytes() > 0)) channel.write(new DefaultHttpChunk(data));
        if (isLast) channel.write(new DefaultHttpChunkTrailer());
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void handleWriteFailed(Throwable cause) { terminate(cause, false); }

    private void terminate(Throwable reason, boolean restoreCurrent) {
        if (terminate != null) return; // Quick check to (potentially) avoid synchronization cost

        RequestContext request;
        synchronized (mutex) {
            if (terminate != null) return;

            terminate = reason;
            available = false;
            request = currentRequest; // If currentRequest = null, request var will be null which is ok
        }

        if (channel == null) {
            listener.connectionFailed(this);
        } else {
            if (channel.isConnected()) try { channel.close(); } catch (Exception ignored) { /* ignored */ }

            boolean hasRequestWithUnfinishedFuture = (request != null) && !request.getFuture().isDone();
            boolean shouldRestore = restoreCurrent && hasRequestWithUnfinishedFuture &&
                                    (request.isIdempotent() || restoreNonIdempotentOperations);
            if (shouldRestore) {
                listener.connectionTerminated(this, Arrays.asList(request));
            } else {
                if (hasRequestWithUnfinishedFuture) request.getFuture().failedWithCause(reason);
                listener.connectionTerminated(this);
            }
        }
    }

    private void receivedContentForCurrentRequest(ChannelBuffer content, boolean last) {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.
        if (discarding) return;

        try {
            if (last) currentRequest.getProcessor().addLastData(content);
            else currentRequest.getProcessor().addData(content);
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            currentRequest.getFuture().failedWhileProcessingResponse(e, currentResponse);
            discarding = true;
        }
    }

    @SuppressWarnings({"unchecked"})
    private void responseForCurrentRequestComplete() {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.

        // Only unlock the future if the contents weren't being discarded. If the contents were being discarded, it
        // means that receivedResponseForCurrentRequest() already triggered the future!
        if (!discarding) {
            currentRequest.getFuture().finishedSuccessfully(currentRequest.getProcessor().getProcessedResponse(),
                                                            currentResponse);
        }

        currentRequestFinished();
    }

    /**
     * Handles the HttpReponse object received and possibly triggers the end of the current request. If the response
     * handler does not want to process the response, then the current request is finished immediately.
     *
     * @param response HttpResponse received
     */
    @SuppressWarnings({"unchecked"})
    private void receivedResponseForCurrentRequest(HttpResponse response) {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.

        currentResponse = response;
        try {
            ResponseProcessor processor = currentRequest.getProcessor();
            if (!processor.willProcessResponse(response)) {
                // Rather than waiting for the full content to arrive (which will be discarded), perform an early
                // trigger on the Future, signalling request is finished. Note that currentRequestFinished() is *not*
                // called in this method, which means that execution of other requests will not be allowed, even though
                // the current request has been terminated. The reason for this is that all incoming data must be safely
                // consumed (and discarded) before another request hits the network - this avoids possible response data
                // mixing. When you *KNOW* for sure that the server supports HTTP/1.1 pipelining, then use the
                // pipelining implementation, PipeliningConnection.

                // Even though the processor does not want to process the response, it might still return some default
                // result, so call getProcessedResponse() on it, rather than passing null to the Future.
                currentRequest.getFuture().finishedSuccessfully(processor.getProcessedResponse(), currentResponse);
                discarding = true;
            } else {
                // Response processor wants to process the contents of this request.
                discarding = false;
            }
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            currentRequest.getFuture().failedWhileProcessingResponse(e, response);
            discarding = true;
        }
    }

    private void currentRequestFinished() {
        // Always called inside a synchronized block.

        RequestContext context = currentRequest;

        boolean keepAlive = isKeepAlive(currentRequest.getRequest()) && isKeepAlive(currentResponse);
        available = keepAlive && isConnected();

        currentRequest = null;
        currentResponse = null;
        continueReceived = false;

        listener.requestFinished(this, context);

        // If it's a non-keepalive connection, mark it as terminated, even though it may not be immediately closed.
        if (!keepAlive) {
            if (terminate == null) terminate(RequestFuture.SHUTTING_DOWN, false);

            // If configured to do so, don't wait for the server to gracefully shutdown the connection
            // and shut it down ourselves...
            if (disconnectIfNonKeepAliveRequest && channel.isConnected()) channel.close();
        }
    }
}
