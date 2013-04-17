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

package com.biasedbit.http.client.timeout;

import com.biasedbit.http.client.future.RequestFuture;
import com.biasedbit.http.client.future.RequestFutureListener;
import com.biasedbit.http.client.util.RequestContext;
import lombok.RequiredArgsConstructor;
import org.jboss.netty.util.internal.ExecutorUtil;

import java.util.concurrent.*;

/**
 * Basic implementation of a {@link TimeoutController} that launches a thread with a {@link TimeoutChecker}.
 * <p/>
 * The {@link TimeoutChecker} will bind itself as a listener to the request future. It will block on a latch until
 * either the provided timeout expires or the operation completes.
 * <p/>
 * If the executor queues the execution of the {@link TimeoutChecker}, it is very likely that when the task starts, the
 * operation is already complete. In this case, the checker will terminate immediately.
 * <h2>Precision vs resource consumption</h1>
 * Precision is how fast a request is cancelled when it's execution times out.
 * This timer can consume more resources (unless its configured to run a single thread), but is potentially more precise
 * than {@link HashedWheelTimeoutController}.
 * <p/>
 * <strong>The more threads it has, the more precise it will be, but also more resources it will consume.</strong>
 * Conversely, the less threads it has, the less precise it will be.
 * <p/>
 * <div class="note">
 * <div class="header>Important note:</div>
 * If an instance is {@linkplain #BasicTimeoutController(java.util.concurrent.Executor) created with an external
 * {@code Executor}}, then this manager <strong>will not</strong> terminate the executor.
 * <p/>
 * If an instance is created with the constructor {@link #BasicTimeoutController(int)}, it will terminate the thread
 * pool when {@link #terminate()} is called.
 * </div>
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class BasicTimeoutController
        implements TimeoutController {

    // properties -----------------------------------------------------------------------------------------------------

    private final Executor executor;

    // internal vars --------------------------------------------------------------------------------------------------

    private final boolean internalExecutor;

    // constructors ---------------------------------------------------------------------------------------------------

    public BasicTimeoutController(int maxThreads) {
        if (maxThreads <= 0) executor = Executors.newCachedThreadPool();
        else executor = Executors.newFixedThreadPool(maxThreads);

        internalExecutor = true;
    }

    public BasicTimeoutController(Executor executor) {
        this.executor = executor;
        internalExecutor = false;
    }

    // TimeoutManager -------------------------------------------------------------------------------------------------

    @Override public boolean init() { return true; }

    @Override public void terminate() { if (internalExecutor) ExecutorUtil.terminate(executor); }

    @Override public void controlTimeout(RequestContext context) { executor.execute(new TimeoutChecker(context)); }

    /**
     * Checks if a given request times out before its execution completes.
     * <p/>
     * When the {@link #run()} method is called, the timeout checker binds itself as a listener to the provided {@link
     * com.biasedbit.http.client.util.RequestContext}.
     * <p/>
     * The motive behind using a {@code TimeoutChecker} instead of having some internal thread in each connection that
     * shares state variables is that on some rare occasions, while executing runs with client and server on the same
     * host, the responseses are actually received faster than the timeout checker thread is launched. If this happens,
     * it means that the {@link com.biasedbit.http.client.connection.Connection} will probably have accepted
     * another request and the timeout checker will actually be timing out a different request than the one originally
     * intended.
     * <p/>
     * Having a per-request checker does hurt performance a bit but it avoids the aforementioned problem. Besides, this
     * small performance slowdown only occurs when the request-response cycle takes 0.01~ to complete which, in real
     * scenarios, is extremely unlikely to ever happen.
     */
    @RequiredArgsConstructor
    public static class TimeoutChecker
            implements Runnable,
                       RequestFutureListener {

        // internal vars ----------------------------------------------------------------------------------------------

        private final RequestContext request;

        private final CountDownLatch latch = new CountDownLatch(1);

        // Runnable ---------------------------------------------------------------------------------------------------

        @SuppressWarnings({"unchecked"})
        @Override public void run() {
            request.getFuture().addListener(this);

            // If operation completed meanwhile, operationComplete() will have been called, the latch will have been
            // counted down to zero and latch.await() will return immediately with true so everything's perfect even in
            // that twisted scenario.
            try {
                // If explicitly released before timing out, nothing to do, request already executed or failed.
                if (latch.await(request.getTimeout(), TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException ignored) { /* ignored */ }

            // Request timed out...
            // If the future has already been unlocked, then no harm is done by calling this method as it won't produce
            // any side effects.
            request.getFuture().failedWithCause(RequestFuture.TIMED_OUT);
        }

        // RequestFutureListener ----------------------------------------------------------------------------------

        @Override public void operationComplete(RequestFuture requestFuture)
                throws Exception {
            latch.countDown();
        }
    }
}
