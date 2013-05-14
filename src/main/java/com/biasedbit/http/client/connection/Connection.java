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

import com.biasedbit.http.client.util.RequestContext;
import org.jboss.netty.channel.ChannelHandler;

/**
 * An HTTP connection to a server.
 * <p/>
 * HTTP requests are dispatched from the {@link com.biasedbit.http.client.HttpClient} to the {@code Connection}s
 * under the form of a {@link com.biasedbit.http.client.util.RequestContext}.
 * <p/>
 * To execute requests in a {@code Connection}, the caller must always ensure the connection can process its request
 * by calling {@link #isAvailable()} prior to {@link #execute(com.biasedbit.http.client.util.RequestContext) execute()}.
 * <p/>
 * Example:
 * <pre class="code">
 * if (connection.isAvailable) {
 *     // This is not guaranteed to work.. Connection may go down or be termianted by other thread meanwhile!
 *     connection.execute(request);
 * }</pre>
 * Implementations of this interface are thread-safe. However, it is ill-advised to used them from multiple threads in
 * order to avoid entropic behavior. If you really want to use a single connection from multiple threads, you should
 * manually synchronise externally. The reason for this is that if both threads call {@link #isAvailable()} at the same
 * time, both will be able to {@linkplain #execute(com.biasedbit.http.client.util.RequestContext) submit requests}, even
 * though the implementation may not accept both (and consequently fail the last one with
 * {@link com.biasedbit.http.client.future.RequestFuture#EXECUTION_REJECTED}).
 * <p/>
 * Example:
 * <pre class="code">
 * synchronized (connection) {
 *     if (connection.isAvailable) {
 *         // This is not guaranteed to work.. Connection may close meanwhile!
 *         connection.execute(request);
 *     }
 * }</pre>
 * The reason for this implementation decision is making the common case fast: a vast majority of the times that
 * {@link #isAvailable()} is called, {@link #execute(com.biasedbit.http.client.util.RequestContext) execute()} will
 * accept the request. So rather than having only execute returning {@code true} or {@code false} based on the
 * connection availibility, this quicker call is a very reliable heuristic to determine if requests can be submitted
 * or not.
 *
 * <div class="note">
 * <div class="header">Note:</div>
 * There is no guarantee that a request will be approved if {@link #isAvailable()} returned true. Even though the
 * odds are extremely slim, the connection may go down between the call to {@link #isAvailable()} and {@link
 * #execute(com.biasedbit.http.client.util.RequestContext) execute()}.
 * <p/>For such cases (and only for such cases)
 * {@link #execute(com.biasedbit.http.client.util.RequestContext) execute()} will return {@code false}
 * rather than fail, the request in order for the caller to be given the chance to retry the same request in another
 * connection.
 * <p/>
 * In every other scenario where {@link #isAvailable()} returns false, calling
 * {@link #execute(com.biasedbit.http.client.util.RequestContext) execute()} <strong>will fail</strong> the request
 * (with cause {@link com.biasedbit.http.client.future.RequestFuture#EXECUTION_REJECTED}).
 * </div>
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface Connection
        extends ChannelHandler {

    /**
     * Shut down the connection, cancelling all requests executing meanwhile.
     * <p/>
     * After a connection is shut down, no more requests will be accepted.
     *
     * @param reason The motive for connection termination.
     */
    void terminate(Throwable reason);

    /**
     * Returns the unique identifier of this connection.
     *
     * @return An identifier of the connection.
     */
    String getId();

    /**
     * Returns the host address to which this connection is connected to.
     *
     * @return The host address to which this connection is currently connected to.
     */
    String getHost();

    /**
     * Returns the port to which this connection is connected to.
     *
     * @return The port of to which this connection is connected to.
     */
    int getPort();

    /**
     * Returns whether this connection is available to process a request. Connections that execute HTTP 1.0 requests
     * will <strong>never</strong> return true after the request has been approved for processing as the socket will
     * be closed by the server.
     *
     * @return true if this connection is connected and ready to execute a request
     */
    boolean isAvailable();

    /**
     * Execute a given request context in this connection.
     * <p/>
     * All calls to this method should first test whether this connection is available or not by calling
     * {@link #isAvailable()} first. Calling this methods while {@link #isAvailable()} would return {@code false} will
     * cause the implementation to reject the request with the reason
     * {@link com.biasedbit.http.client.future.RequestFuture#EXECUTION_REJECTED} and return {@code true},
     * meaning the request was consumed (and failed).
     * <p/>
     * The exception to the above rule is when the request is submitted and the connection goes down meanwhile. In this
     * case, the request <strong>will not</strong> be marked as failed and this method will return {@code false} so that
     * the caller may retry the same request in another connection.
     * <p/>
     * Implementations of this method that return {@code true} <strong>MUST</strong> call the connection listener's
     * {@link ConnectionListener#requestFinished(Connection, com.biasedbit.http.client.util.RequestContext)} when the
     * request completes (or fails) unless the connection terminates due to other reasons.
     * <p/>
     * You should always, <strong>always</strong> test first with {@link #isAvailable()}.
     * <p/>
     * This is a non-blocking call.
     *
     * @param context Request execution context.
     *
     * @return {@code true} if the request was accepted, {@code false} otherwise. If a request is accepted, the
     * {@code Connection} becomes responsible for calling
     * {@link com.biasedbit.http.client.future.DefaultRequestFuture#failedWithCause(Throwable) failedWithCause()} or
     * {@link com.biasedbit.http.client.future.DefaultRequestFuture#finishedSuccessfully(Object,
     * org.jboss.netty.handler.codec.http.HttpResponse) finishedSuccessfully()} on it.
     */
    boolean execute(RequestContext context);
}
