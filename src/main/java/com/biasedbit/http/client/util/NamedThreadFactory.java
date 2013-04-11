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

package com.biasedbit.http.client.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * As seen on <a href="http://biasedbit.com/naming-threads-created-with-the-executorservice/">TV</a>.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class NamedThreadFactory
        implements ThreadFactory {

    // internal vars --------------------------------------------------------------------------------------------------

    private final ThreadGroup group;
    private final String      namePrefix;

    private final AtomicInteger threadNumber = new AtomicInteger(1);

    // constructors ---------------------------------------------------------------------------------------------------

    public NamedThreadFactory(String namePrefix) {
        this.namePrefix = String.format("%s-thread", namePrefix);

        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
    }

    // ThreadFactory --------------------------------------------------------------------------------------------------

    @Override public Thread newThread(Runnable runnable) {
        Thread t = new Thread(group, runnable, namePrefix + threadNumber.getAndIncrement(), 0L);
        if (t.isDaemon()) t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY);

        return t;
    }
}
