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

import com.biasedbit.http.client.connection.Connection;
import lombok.SneakyThrows;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultRequestFuture<T>
        implements RequestFuture<T> {

    // internal vars --------------------------------------------------------------------------------------------------

    private final long creation = System.nanoTime();

    private T                              result;
    private HttpResponse                   response;
    private boolean                        done;
    private List<RequestFutureListener<T>> listeners;
    private Throwable                      cause;
    private int                            waiters;
    private Connection                     connection;

    private long executionStart = -1;
    private long executionEnd   = -1;

    // RequestFuture ----------------------------------------------------------------------------------------------

    @Override public T getProcessedResult() { return result; }

    @Override public HttpResponse getResponse() { return response; }

    @Override public HttpResponseStatus getStatus() {
        if (response == null) return null;
        else return response.getStatus();
    }

    @Override public int getResponseStatusCode() {
        if (response == null) return -1;
        else return response.getStatus().getCode();
    }

    @Override public boolean hasSuccessfulResponse() {
        int code = getResponseStatusCode();
        return (code >= 200) && (code <= 299);
    }

    @Override public void markExecutionStart() { executionStart = System.nanoTime(); }

    @Override public long getExecutionTime() {
        if (done) return executionStart == -1 ? 0 : (executionEnd - executionStart) / 1000000;
        else return -1;
    }

    @Override public long getExistenceTime() {
        if (done) return (executionEnd - creation) / 1000000;
        else return (System.nanoTime() - creation) / 1000000;
    }

    @Override public boolean isDone() { return done; }

    @Override public boolean isSuccessful() { return cause == null; }

    @Override public boolean isCancelled() { return cause == CANCELLED; }

    @Override public Throwable getCause() { return cause; }

    @Override public boolean cancel() {
        synchronized (this) {
            if (done) return false;

            executionEnd = System.nanoTime();
            cause = CANCELLED;
            done = true;

            // The connection must be killed in order for the request to effectively be cancelled
            if (connection != null) connection.terminate(CANCELLED);

            if (waiters > 0) notifyAll();
        }

        notifyListeners();
        return true;
    }

    @Override public void addListener(RequestFutureListener<T> listener) {
        synchronized (this) {
            if (done) {
                notifyListener(listener);
            } else {
                if (listeners == null) listeners = new ArrayList<>(1);
                listeners.add(listener);
            }
        }
    }

    @Override public void removeListener(RequestFutureListener<T> listener) {
        synchronized (this) {
            if (done) return;

            if (listeners != null) listeners.remove(listener);
        }
    }

    @Override public RequestFuture<T> await()
            throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();

        synchronized (this) {
            while (!done) {
                waiters++;
                try {
                    wait();
                } finally {
                    waiters--;
                }
            }
        }

        return this;
    }

    @Override public boolean await(long timeout, TimeUnit unit)
            throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override public boolean await(long timeoutMillis)
            throws InterruptedException {
        return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override public RequestFuture<T> awaitUninterruptibly() {
        boolean interrupted = false;
        synchronized (this) {
            while (!done) {
                waiters++;
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    waiters--;
                }
            }
        }

        // Preserve interruption.
        if (interrupted) Thread.currentThread().interrupt();

        return this;
    }

    @SneakyThrows(InterruptedException.class)
    @Override public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return await0(unit.toNanos(timeout), false);
    }

    @SneakyThrows(InterruptedException.class)
    @Override public boolean awaitUninterruptibly(long timeoutMillis) {
        return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), false);
    }

    // interface ------------------------------------------------------------------------------------------------------

    // TODO review this
    public void attachConnection(Connection connection) { this.connection = connection; }

    public boolean finishedSuccessfully(T processedResponse, HttpResponse response) {
        synchronized (this) {
            if (done) return false;

            executionEnd = System.nanoTime();
            done = true;
            result = processedResponse;
            this.response = response;

            if (waiters > 0) notifyAll();
        }

        notifyListeners();
        return true;
    }

    public boolean failedWithCause(Throwable cause) {
        synchronized (this) {
            if (done) return false; // Allow only once.

            executionEnd = System.nanoTime();
            this.cause = cause;
            done = true;

            if (connection != null) connection.terminate(cause);

            if (waiters > 0) notifyAll();
        }

        notifyListeners();
        return true;
    }

    public boolean failedWhileProcessingResponse(Throwable cause, HttpResponse response) {
        synchronized (this) {
            if (done) return false;

            executionEnd = System.nanoTime();
            this.response = response;
            this.cause = cause;
            done = true;

            if (waiters > 0) notifyAll();
        }

        notifyListeners();
        return true;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void notifyListeners() {
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()
        if (listeners == null) return; // Not testing for isEmpty since removing listeners is quite rare...

        for (RequestFutureListener<T> listener : listeners) notifyListener(listener);
    }

    private void notifyListener(RequestFutureListener<T> listener) {
        try {
            listener.operationComplete(this);
        } catch (Throwable t) {
            System.err.println("An exception was thrown by an instance of " + listener.getClass().getSimpleName());
            t.printStackTrace();
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable)
            throws InterruptedException {
        // Implementation straight out of Netty's own DefaultChannelFuture
        if (interruptable && Thread.interrupted()) throw new InterruptedException();

        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (done) return true;
                else if (waitTime <= 0) return done;

                waiters++;
                try {
                    while (true) {
                        try {
                            wait(waitTime / 1000000, (int) (waitTime % 1000000));
                        } catch (InterruptedException e) {
                            if (interruptable) throw e;
                            else interrupted = true;
                        }

                        if (done) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) return done;
                        }
                    }
                } finally {
                    waiters--;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    // object overrides -----------------------------------------------------------------------------------------------

    @Override public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("RequestFuture{")
                .append("existenceTime=").append(getExistenceTime())
                .append(", executionTime=").append(getExecutionTime());

        if (!isDone()) builder.append(", inProgress");
        else if (isSuccessful()) builder.append(", succeeded (code ").append(response.getStatus().getCode()).append(')');
        else builder.append(", failed (").append(cause).append(')');

        return builder.append('}').toString();
    }
}
