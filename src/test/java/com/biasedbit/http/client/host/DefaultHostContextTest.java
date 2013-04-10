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

package com.biasedbit.http.client.host;

import com.biasedbit.http.client.HostContextTestUtil;
import com.biasedbit.http.client.HttpConnectionTestUtil;
import com.biasedbit.http.client.HttpRequestContext;
import com.biasedbit.http.client.future.HttpRequestFuture;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHostContextTest {

    // internal vars --------------------------------------------------------------------------------------------------

    private AbstractHostContext hostContext;
    private List<HttpRequestContext<Object>> requestContexts;

    // tests ----------------------------------------------------------------------------------------------------------

    @Before
    public void setUp() {
        String host = "localhost";
        int port = 80;
        this.hostContext = new DefaultHostContext(host, port, 2);
        this.requestContexts = new ArrayList<HttpRequestContext<Object>>(4);
        for (int i = 0; i < 4; i++) {
            HttpRequestContext<Object> requestContext = HostContextTestUtil.generateDummyContext(host, port);
            this.requestContexts.add(requestContext);
            this.hostContext.addToQueue(requestContext);
        }
    }

    @Test
    public void testDrainQueueWithAvailableConnection() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().totalConnections());
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        assertEquals(1, this.hostContext.getConnectionPool().totalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.DRAINED, this.hostContext.drainQueue());
        assertEquals(3, this.hostContext.getQueue().size());
    }

    @Test
    public void testDrainQueueWithNoConnection() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().totalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.OPEN_CONNECTION, this.hostContext.drainQueue());
        assertEquals(4, this.hostContext.getQueue().size());
    }

    @Test
    public void testDrainQueueWithAllConnectionsExausted() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().totalConnections());
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.NeverAvailableHttpConnection("id", "host", 0, null));
        this.hostContext.getConnectionPool().connectionOpening();
        assertEquals(2, this.hostContext.getConnectionPool().totalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.NOT_DRAINED, this.hostContext.drainQueue());
        assertEquals(4, this.hostContext.getQueue().size());
    }

    @Test
    public void testDrainQueueWithQueueEmpty() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().totalConnections());
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        assertEquals(2, this.hostContext.getConnectionPool().totalConnections());
        this.hostContext.drainQueue();
        this.hostContext.drainQueue();
        this.hostContext.drainQueue();
        this.hostContext.drainQueue();
        assertEquals(0, this.hostContext.getQueue().size());
        assertEquals(HostContext.DrainQueueResult.QUEUE_EMPTY, this.hostContext.drainQueue());
        assertEquals(0, this.hostContext.getQueue().size());
    }

    @Test
    public void testFailAllRequests() throws Exception {
        this.hostContext.failAllRequests(HttpRequestFuture.CONNECTION_LOST);
        for (HttpRequestContext<Object> request : this.requestContexts) {
            assertFalse(request.getFuture().isSuccess());
            assertEquals(HttpRequestFuture.CONNECTION_LOST, request.getFuture().getCause());
        }
    }
}
