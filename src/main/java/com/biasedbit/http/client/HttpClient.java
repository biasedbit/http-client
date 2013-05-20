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

import com.biasedbit.http.client.future.DataSinkListener;
import com.biasedbit.http.client.future.RequestFuture;
import com.biasedbit.http.client.processor.ResponseProcessor;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Executes {@code org.jboss.netty.handler.codec.http.HttpRequest}s.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface HttpClient {

    /**
     * Initialise the instance.
     * <p/>
     * All clients must be initialised prior to their usage. Calling any of the {@code execute()} methods on a client
     * prior to calling {@code init()} will result in a {@link CannotExecuteRequestException}.
     *
     * @return {@code true} if successfully initialised, {@code false} otherwise.
     */
    boolean init();

    boolean isInitialized();
    /**
     * Terminate the instance.
     * <p/>
     * When a client is no longer needed, it should be properly shut down by calling {@code terminate()} in order to
     * release resources.
     * <p/>
     * Terminating a client while it is still processing requests will result in all requests being finished (failing),
     * no matter if they are in the event queue, request queue or already executing inside a connection.
     */
    void terminate();

    /**
     * Executes a request to a given host on port 80 with default timeout of the client.
     *
     * @param host      Destination host.
     * @param port      Destination port.
     * @param request   Request to execute.
     * @param processor Response body processor.
     *
     * @return Future associated with the operation.
     *
     * @throws CannotExecuteRequestException Thrown when the request is invalid or the client can no longer accept
     *                                       requests, either due to termination or full queue.
     */
    <T> RequestFuture<T> execute(String host, int port, HttpRequest request, ResponseProcessor<T> processor)
            throws CannotExecuteRequestException;

    /**
     * Version of {@code execute()} that allows manual definition of timeout.
     *
     * @param host      Destination host.
     * @param port      Destination port.
     * @param timeout   Manual timeout for the operation to complete. This timeout is related to the request execution
     *                  time once it enters a connection, not the full lifecycle of the request. If there are many
     *                  events in the event queue it is likely that the request will have to wait a couple of
     *                  milliseconds before actually entering a connection.
     * @param request   Request to execute.
     * @param processor Response body processor.
     *
     * @return Future associated with the operation.
     *
     * @throws CannotExecuteRequestException Thrown when the request is invalid or the client can no longer accept
     *                                       requests, either due to termination or full queue.
     */
    <T> RequestFuture<T> execute(String host, int port, int timeout, HttpRequest request,
                                     ResponseProcessor<T> processor)
            throws CannotExecuteRequestException;

    <T> RequestFuture<T> executeWithDataSink(String host, int port, int timeout, HttpRequest request,
                                             ResponseProcessor<T> processor, DataSinkListener dataSinkListener)
            throws CannotExecuteRequestException;

    /**
     * Version {@code execute()} that discards the result of the operation.
     *
     * @param host      Destination host.
     * @param port      Destination port.
     * @param request   Request to execute.
     *
     * @return Future associated with the operation.
     *
     * @throws CannotExecuteRequestException Thrown when the request is invalid or the client can no longer accept
     *                                       requests, either due to termination or full queue.
     */
    RequestFuture<Object> execute(String host, int port, HttpRequest request)
            throws CannotExecuteRequestException;

    boolean isHttps();
}
