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

package com.biasedbit.http.future;

import com.biasedbit.http.connection.HttpConnection;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHttpRequestFuture<T>
        implements HttpRequestFuture<T> {

    // properties -----------------------------------------------------------------------------------------------------

    private final boolean cancellable;

    // internal vars --------------------------------------------------------------------------------------------------

    private       T                                  result;
    private       HttpResponse                       response;
    private       Object                             attachment;
    private       boolean                            done;
    private       List<HttpRequestFutureListener<T>> listeners;
    private       Throwable                          cause;
    private       int                                waiters;
    private       long                               executionStart;
    private       long                               executionEnd;
    private final long                               creation;
    private       HttpConnection                     connection;

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHttpRequestFuture() {
        this(false);
    }

    public DefaultHttpRequestFuture(boolean cancellable) {
        this.cancellable = cancellable;
        this.creation = System.nanoTime();
        this.executionStart = -1;
    }

    // HttpRequestFuture ----------------------------------------------------------------------------------------------

    @Override
    public T getProcessedResult() {
        return this.result;
    }

    @Override
    public HttpResponse getResponse() {
        return this.response;
    }

    @Override
    public HttpResponseStatus getStatus() {
        if (this.response == null) {
            return null;
        }
        return this.response.getStatus();
    }

    @Override
    public int getResponseStatusCode() {
        if (this.response == null) {
            return -1;
        }

        return this.response.getStatus().getCode();
    }

    @Override
    public boolean isSuccessfulResponse() {
        int code = this.getResponseStatusCode();
        return (code >= 200) && (code <= 299);
    }

    @Override
    public void markExecutionStart() {
        this.executionStart = System.nanoTime();
    }

    @Override
    public long getExecutionTime() {
        if (this.done) {
            return this.executionStart == -1 ? 0 : (this.executionEnd - this.executionStart) / 1000000;
        } else {
            return -1;
        }
    }

    @Override
    public long getExistenceTime() {
        if (this.done) {
            return (this.executionEnd - this.creation) / 1000000;
        } else {
            return (System.nanoTime() - this.creation) / 1000000;
        }
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

    @Override
    public boolean isSuccess() {
        return this.response != null;
    }

    @Override
    public boolean isCancelled() {
        return cause == CANCELLED;
    }

    @Override
    public Throwable getCause() {
        return this.cause;
    }

    @Override
    public boolean cancel() {
        if (!this.cancellable) {
            return false;
        }

        synchronized (this) {
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.cause = CANCELLED;
            this.done = true;

            // The connection must be killed in order for the request to effectively be cancelled
            this.connection.terminate(CANCELLED);

            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setSuccess(T processedResponse, HttpResponse response) {
        synchronized (this) {
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.done = true;
            this.result = processedResponse;
            this.response = response;
            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setFailure(Throwable cause) {
        synchronized (this) {
            // Allow only once.
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.cause = cause;
            this.done = true;
            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setFailure(HttpResponse response, Throwable cause) {
        synchronized (this) {
            // Allow only once.
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.response = response;
            this.cause = cause;
            this.done = true;
            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public void addListener(HttpRequestFutureListener<T> listener) {
        synchronized (this) {
            if (this.done) {
                this.notifyListener(listener);
            } else {
                if (this.listeners == null) {
                    this.listeners = new ArrayList<HttpRequestFutureListener<T>>(1);
                }
                this.listeners.add(listener);
            }
        }
    }

    @Override
    public void removeListener(HttpRequestFutureListener<T> listener) {
        synchronized (this) {
            if (!this.done) {
                if (this.listeners != null) {
                    this.listeners.remove(listener);
                }
            }
        }
    }

    @Override
    public Object getAttachment() {
        return attachment;
    }

    @Override
    public void setAttachment(Object attachment) {
        this.attachment = attachment;
    }

    @Override
    public HttpRequestFuture<T> await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!this.done) {
                waiters++;
                try {
                    this.wait();
                } finally {
                    waiters--;
                }
            }
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return this.await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public HttpRequestFuture<T> awaitUninterruptibly() {
        boolean interrupted = false;
        synchronized (this) {
            while (!this.done) {
                this.waiters++;
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    this.waiters--;
                }
            }
        }

        // Preserve interruption.
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    // interface ------------------------------------------------------------------------------------------------------

    public void attachConnection(HttpConnection connection) {
        this.connection = connection;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void notifyListeners() {
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()
        if (this.listeners == null) {
            // Not testing for isEmpty as it is ultra rare someone adding a listener and then removing it...
            return;
        }

        for (HttpRequestFutureListener<T> listener : listeners) {
            this.notifyListener(listener);
        }
    }

    private void notifyListener(HttpRequestFutureListener<T> listener) {
        try {
            listener.operationComplete(this);
        } catch (Throwable t) {
            System.err.println("An exception was thrown by an instance of " + listener.getClass().getSimpleName());
            t.printStackTrace();
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException();
        }

        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (this.done) {
                    return true;
                } else if (waitTime <= 0) {
                    return this.done;
                }

                this.waiters++;
                try {
                    for (;;) {
                        try {
                            this.wait(waitTime / 1000000, (int) (waitTime % 1000000));
                        } catch (InterruptedException e) {
                            if (interruptable) {
                                throw e;
                            } else {
                                interrupted = true;
                            }
                        }

                        if (this.done) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) {
                                return this.done;
                            }
                        }
                    }
                } finally {
                    this.waiters--;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // object overrides -----------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("HttpRequestFuture{")
                .append("existenceTime=").append(this.getExistenceTime())
                .append(", executionTime=").append(this.getExecutionTime());
        if (!this.isDone()) {
            builder.append(", inProgress");
        } else if (this.isSuccess()) {
            builder.append(", succeeded (code ").append(this.response.getStatus().getCode()).append(')');
        } else {
            builder.append(", failed (").append(this.cause).append(')');
        }
        return builder.append('}').toString();
    }
}
