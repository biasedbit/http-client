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

package com.biasedbit.http.client.future;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface RequestFuture<T> {

    static final Throwable INTERRUPTED        = new Throwable("Interrupted");
    static final Throwable CANCELLED          = new Throwable("Cancelled");
    static final Throwable CANNOT_CONNECT     = new Throwable("Cannot connect");
    static final Throwable CONNECTION_LOST    = new Throwable("Connection lost");
    static final Throwable SHUTTING_DOWN      = new Throwable("Shutting down");
    static final Throwable EXECUTION_REJECTED = new Throwable("Execution rejected by connection");
    static final Throwable TIMED_OUT          = new Throwable("Request execution timed out");
    static final Throwable INVALID_REDIRECT   = new Throwable("Redirect without Location header");

    T getProcessedResult();

    HttpResponse getResponse();

    HttpResponseStatus getStatus();

    int getResponseStatusCode();

    boolean hasSuccessfulResponse();

    void markExecutionStart();

    long getExecutionTime();

    long getExistenceTime();

    boolean isDone();

    boolean isSuccessful();

    boolean isCancelled();

    Throwable getCause();

    boolean cancel();

    void addListener(RequestFutureListener<T> listener);

    void removeListener(RequestFutureListener<T> listener);

    RequestFuture<T> await()
            throws InterruptedException;

    boolean await(long timeout, TimeUnit unit)
            throws InterruptedException;

    boolean await(long timeoutMillis)
            throws InterruptedException;

    RequestFuture<T> awaitUninterruptibly();

    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    boolean awaitUninterruptibly(long timeoutMillis);
}
