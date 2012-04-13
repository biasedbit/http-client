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

package com.biasedbit.http.client;

import com.biasedbit.http.DummyHttpServer;
import com.biasedbit.http.HttpConnectionTestUtil;
import com.biasedbit.http.HttpRequestContext;
import com.biasedbit.http.connection.PipeliningHttpConnectionFactory;
import com.biasedbit.http.future.HttpRequestFuture;
import com.biasedbit.http.processor.DiscardProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class AbstractHttpClientTest {

    // internal vars --------------------------------------------------------------------------------------------------

    private DummyHttpServer server;

    // tests ----------------------------------------------------------------------------------------------------------

    @Before
    public void setUp() {
        this.server = new DummyHttpServer("localhost", 8081, false);
    }

    @After
    public void tearDown() {
        if (this.server != null) {
            this.server.terminate();
        }
    }

    @Test
    public void testCancellationOfAllPendingRequests() throws Exception {
        // This test tests that *ALL* futures are unlocked when a premature shutdown is called on the client, no matter
        // where they are:
        //   1) the event queue (pre-processing)
        //   2) the request-queue (processed and queued)
        //   3) inside the connection (executing).
        // Ensuring all futures are unlocked under all circumstances is vital to keep this library from generating code
        // stalls.

        this.server.setFailureProbability(0.0f);
        this.server.setResponseLatency(50L);
        assertTrue(this.server.init());

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
        //factory.setDebug(true);
        final HttpClient client = factory.getClient();
        assertTrue(client.init());

        List<HttpRequestFuture<Object>> futures = new ArrayList<HttpRequestFuture<Object>>();
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        for (int i = 0; i < 1000; i++) {
            futures.add(client.execute("localhost", 8081, request, new DiscardProcessor()));
        }

        // server is configured to sleep for 50ms in each request so only the first 3 should complete.
        Thread.sleep(150L);
        client.terminate();

        long complete = 0;
        for (HttpRequestFuture<Object> future : futures) {
            assertTrue(future.isDone());
            if (future.isSuccess()) {
                complete++;
            } else {
                assertEquals(HttpRequestFuture.SHUTTING_DOWN, future.getCause());
            }
        }

        // Should print 3 or 6/1000... Really depends on the computer though...
        System.out.println(complete + "/1000 requests were executed. All others failed with cause: " +
                           HttpRequestFuture.SHUTTING_DOWN);
    }

    @Test
    public void testCancellationOfAllPendingRequestsWithPipelining() throws Exception {
        this.server.setFailureProbability(0.0f);
        this.server.setResponseLatency(50L);
        assertTrue(this.server.init());

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
        factory.setConnectionFactory(new PipeliningHttpConnectionFactory());
        //factory.setDebug(true);
        final HttpClient client = factory.getClient();
        assertTrue(client.init());

        List<HttpRequestFuture<Object>> futures = new ArrayList<HttpRequestFuture<Object>>();
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        for (int i = 0; i < 1000; i++) {
            futures.add(client.execute("localhost", 8081, request, new DiscardProcessor()));
        }

        // server is configured to sleep for 50ms in each request so only the first 3 should complete.
        Thread.sleep(150L);
        client.terminate();

        long complete = 0;
        for (HttpRequestFuture<Object> future : futures) {
            assertTrue(future.isDone());
            if (future.isSuccess()) {
                complete++;
            } else {
                assertEquals(HttpRequestFuture.SHUTTING_DOWN, future.getCause());
            }
        }

        // Should print 3 or 6/1000... Really depends on the computer though...
        System.out.println(complete + "/1000 requests were executed. All others failed with cause: " +
                           HttpRequestFuture.SHUTTING_DOWN);
    }


    @Test
    public void testRequestTimeout() {
        this.server.setFailureProbability(0.0f);
        this.server.setResponseLatency(1000L); // because default hashedwheeltimer has 500ms variable precision
        assertTrue(this.server.init());

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
        //factory.setDebug(true);
        final HttpClient client = factory.getClient();
        assertTrue(client.init());

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpRequestFuture<Object> future = client.execute("localhost", 8081, 50, request, new DiscardProcessor());
        assertTrue(future.awaitUninterruptibly(5000L));
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
        assertEquals(HttpRequestFuture.TIMED_OUT, future.getCause());
        client.terminate();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testContextNonCleanup() throws Exception {
        assertTrue(this.server.init());

        AbstractHttpClient client = new VerboseHttpClient();
        client.setCleanupInactiveHostContexts(false);
        HttpConnectionTestUtil.AlwaysAvailableConnectionFactory factory =
                new HttpConnectionTestUtil.AlwaysAvailableConnectionFactory();
        client.setConnectionFactory(factory);
        assertTrue(client.init());

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpRequestFuture future = client.execute("localhost", 8081, request);
        Thread.sleep(300L);
        assertEquals(1, client.getContextMap().size());
        HttpConnectionTestUtil.AlwaysAvailableHttpConnection connection = factory.getConnectionsGenerated().get(0);
        connection.getListener().connectionOpened(connection);
        connection.getListener().requestFinished(connection, new HttpRequestContext("localhost", 8081, 10, request,
                                                                                    new DiscardProcessor(), future));
        future.awaitUninterruptibly();
        connection.getListener().connectionTerminated(connection);
        Thread.sleep(300L);

        assertEquals(1, client.getContextMap().size());
        client.terminate();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testContextCleanup() throws Exception {
        assertTrue(this.server.init());

        AbstractHttpClient client = new VerboseHttpClient();
        client.setCleanupInactiveHostContexts(true);
        HttpConnectionTestUtil.AlwaysAvailableConnectionFactory factory =
                new HttpConnectionTestUtil.AlwaysAvailableConnectionFactory();
        client.setConnectionFactory(factory);
        assertTrue(client.init());

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpRequestFuture future = client.execute("localhost", 8081, request);
        Thread.sleep(300L);
        assertEquals(1, client.getContextMap().size());
        HttpConnectionTestUtil.AlwaysAvailableHttpConnection connection = factory.getConnectionsGenerated().get(0);
        connection.getListener().connectionOpened(connection);
        connection.getListener().requestFinished(connection, new HttpRequestContext("localhost", 8081, 10, request,
                                                                                    new DiscardProcessor(), future));
        future.awaitUninterruptibly();
        connection.getListener().connectionTerminated(connection);
        Thread.sleep(300L);

        assertEquals(0, client.getContextMap().size());
        client.terminate();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void test3ContextCleanup() throws Exception {
        assertTrue(this.server.init());
        DummyHttpServer server2 = new DummyHttpServer("localhost", 8082, false);
        DummyHttpServer server3 = new DummyHttpServer("localhost", 8083, false);
        assertTrue(server2.init());
        assertTrue(server3.init());

        try {
            AbstractHttpClient client = new VerboseHttpClient();
            client.setCleanupInactiveHostContexts(true);
            HttpConnectionTestUtil.AlwaysAvailableConnectionFactory factory =
                    new HttpConnectionTestUtil.AlwaysAvailableConnectionFactory();
            client.setConnectionFactory(factory);
            assertTrue(client.init());

            HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            HttpRequestFuture future = client.execute("localhost", 8081, request);
            HttpRequestFuture future2 = client.execute("localhost", 8082, request);
            HttpRequestFuture future3 = client.execute("localhost", 8083, request);
            Thread.sleep(300L);

            assertEquals(3, client.getContextMap().size());

            HttpConnectionTestUtil.AlwaysAvailableHttpConnection connection = factory.getConnectionsGenerated().get(0);
            HttpConnectionTestUtil.AlwaysAvailableHttpConnection connection2 = factory.getConnectionsGenerated().get(1);
            HttpConnectionTestUtil.AlwaysAvailableHttpConnection connection3 = factory.getConnectionsGenerated().get(2);
            connection.getListener().connectionOpened(connection);
            connection2.getListener().connectionOpened(connection2);
            connection3.getListener().connectionOpened(connection3);

            connection.getListener()
                    .requestFinished(connection, new HttpRequestContext("localhost", 8081, 10, request,
                                                                        new DiscardProcessor(), future));
            connection2.getListener()
                    .requestFinished(connection2, new HttpRequestContext("localhost", 8082, 10, request,
                                                                         new DiscardProcessor(), future2));
            connection3.getListener()
                    .requestFinished(connection3, new HttpRequestContext("localhost", 8083, 10, request,
                                                                         new DiscardProcessor(), future3));
            future.awaitUninterruptibly();
            future2.awaitUninterruptibly();
            future3.awaitUninterruptibly();
            connection.getListener().connectionTerminated(connection);
            connection2.getListener().connectionTerminated(connection2);
            connection3.getListener().connectionTerminated(connection3);
            Thread.sleep(300L);

            assertEquals(0, client.getContextMap().size());
            client.terminate();
        } finally {
            server2.terminate();
            server3.terminate();
        }
    }
}
