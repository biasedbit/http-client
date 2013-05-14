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
import org.jboss.netty.util.*;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of timeout manager that uses an underlying {@link HashedWheelTimer} to manage timeouts.
 * <p/>
 * If an external {@link HashedWheelTimer} is provided, an instance of this class <strong>will not</strong> call
 * {@link HashedWheelTimer#start()} nor {@link HashedWheelTimer#start()} when {@link #init()} and {@link #terminate()}
 * are called.
 * <h2>Precision vs resource consumption</h2>
 * Since {@link HashedWheelTimer} has a periodic checking interval, this timer is not very precise. If, however, you
 * configure the {@link HashedWheelTimer} with a very small interval, it will increase precision at the cost of more
 * periodic checks (wasted CPU).
 * <p/>
 * The default tick is 500ms. This means that in the worst case scenario, a request will be cancelled 500ms over the
 * timeout set. If this is acceptable, use this implementation rather than {@link BasicTimeoutController}. You can
 * always configure a lower tick time although for HTTP requests even 1 second over the limit is okay most of the times.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class HashedWheelTimeoutController
        implements TimeoutController {

    // properties -----------------------------------------------------------------------------------------------------

    private final HashedWheelTimer timer;

    // constructors ---------------------------------------------------------------------------------------------------

    public HashedWheelTimeoutController(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        timer = new HashedWheelTimer(tickDuration, unit, ticksPerWheel);
    }

    public HashedWheelTimeoutController() { this(500, TimeUnit.MILLISECONDS, 512); }

    // TimeoutManager -------------------------------------------------------------------------------------------------

    @Override public boolean init() {
        timer.start();

        return true;
    }

    @Override public void terminate() { timer.stop(); }

    @SuppressWarnings({"unchecked"})
    @Override public void controlTimeout(final RequestContext context) {
        TimerTask task = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (timeout.isExpired()) context.getFuture().failedWithCause(RequestFuture.TIMED_OUT);
            }
        };
        Timeout t = timer.newTimeout(task, context.getTimeout(), TimeUnit.MILLISECONDS);
        context.getFuture().addListener(new FutureTimeout(t));
    }

    // private classes ------------------------------------------------------------------------------------------------

    private static class FutureTimeout<T>
            implements RequestFutureListener<T> {

        // internal vars ----------------------------------------------------------------------------------------------

        private final WeakReference<Timeout> httpTimeout; // A weak reference to avoid circular references

        // constructors -----------------------------------------------------------------------------------------------

        public FutureTimeout(final Timeout t) { httpTimeout = new WeakReference<>(t); }

        // RequestFutureListener ----------------------------------------------------------------------------------

        @Override public void operationComplete(final RequestFuture<T> future)
                throws Exception {
            Timeout t = httpTimeout.get();
            if (t != null) t.cancel();
        }
    }
}
