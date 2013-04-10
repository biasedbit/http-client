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

import com.biasedbit.http.client.HostContextTestUtil;
import com.biasedbit.http.client.HttpRequestContext;
import com.biasedbit.http.client.future.HttpRequestFuture;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class HashedWheelTimeoutManagerTest {

    @Test
    public void testHashedWheelRequestTimeout() throws Exception {
        test();
    }

    private void test() throws InterruptedException {
        TimeoutController manager = new HashedWheelTimeoutController(100, TimeUnit.MILLISECONDS, 512);
        manager.init();
        HttpRequestContext context = HostContextTestUtil.generateDummyContext("localhost", 8080, 500);
        manager.manageRequestTimeout(context);

        Thread.sleep(2000L);

        System.out.println("hwt: " + context.getFuture().getExistenceTime());
        assertTrue(context.getFuture().isDone());
        assertFalse(context.getFuture().isSuccess());
        assertNotNull(context.getFuture().getCause());
        assertEquals(HttpRequestFuture.TIMED_OUT, context.getFuture().getCause());
        manager.terminate();
    }
}
