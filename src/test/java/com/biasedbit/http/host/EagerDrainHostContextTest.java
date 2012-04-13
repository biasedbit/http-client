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

package com.biasedbit.http.host;

import com.biasedbit.http.HostContextTestUtil;
import com.biasedbit.http.HttpConnectionTestUtil;
import com.biasedbit.http.HttpRequestContext;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class EagerDrainHostContextTest {

    // internal vars --------------------------------------------------------------------------------------------------

    private AbstractHostContext hostContext;

    // tests ----------------------------------------------------------------------------------------------------------

    @Before
    public void setUp() {
        String host = "localhost";
        int port = 80;
        this.hostContext = new EagerDrainHostContext(host, port, 2);
        for (int i = 0; i < 4; i++) {
            HttpRequestContext<Object> requestContext = HostContextTestUtil.generateDummyContext(host, port);
            this.hostContext.addToQueue(requestContext);
        }
    }

    @Test
    public void testDrainQueueWithAvailableConnection() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().getTotalConnections());
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        assertEquals(2, this.hostContext.getConnectionPool().getTotalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.DRAINED, this.hostContext.drainQueue());
        assertEquals(0, this.hostContext.getQueue().size());
    }
}
