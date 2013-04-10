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

package com.biasedbit.http.client;

import com.biasedbit.http.client.HttpRequestContext;
import com.biasedbit.http.client.connection.HttpConnection;
import com.biasedbit.http.client.connection.HttpConnectionFactory;
import com.biasedbit.http.client.connection.HttpConnectionListener;
import com.biasedbit.http.client.timeout.TimeoutController;
import com.biasedbit.http.client.timeout.TimeoutController;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class HttpConnectionTestUtil {

    public static class AlwaysAvailableConnectionFactory implements HttpConnectionFactory {

        private final List<AlwaysAvailableHttpConnection> connectionsGenerated =
                new ArrayList<AlwaysAvailableHttpConnection>();

        @Override
        public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                               TimeoutController manager) {
            AlwaysAvailableHttpConnection connection = new AlwaysAvailableHttpConnection(id, host, port, listener);
            this.connectionsGenerated.add(connection);
            return connection;
        }

        @Override
        public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                               TimeoutController manager, Executor executor) {
            AlwaysAvailableHttpConnection connection = new AlwaysAvailableHttpConnection(id, host, port, listener);
            this.connectionsGenerated.add(connection);
            return connection;
        }

        public List<AlwaysAvailableHttpConnection> getConnectionsGenerated() {
            return connectionsGenerated;
        }
    }

    public static class NeverAvailableConnectionFactory implements HttpConnectionFactory {

        private final List<NeverAvailableHttpConnection> connectionsGenerated =
                new ArrayList<NeverAvailableHttpConnection>();

        @Override
        public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                               TimeoutController manager) {
            NeverAvailableHttpConnection connection = new NeverAvailableHttpConnection(id, host, port, listener);
            this.connectionsGenerated.add(connection);
            return connection;
        }

        @Override
        public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                               TimeoutController manager, Executor executor) {
            NeverAvailableHttpConnection connection = new NeverAvailableHttpConnection(id, host, port, listener);
            this.connectionsGenerated.add(connection);
            return connection;
        }

        public List<NeverAvailableHttpConnection> getConnectionsGenerated() {
            return connectionsGenerated;
        }
    }

    public static class AlwaysAvailableHttpConnection extends SimpleChannelUpstreamHandler
            implements HttpConnection {

        private final String id;
        private final String host;
        private final int port;
        private HttpConnectionListener listener;
        private int requestsExecuted = 0;

        public AlwaysAvailableHttpConnection(String id, String host, int port, HttpConnectionListener listener) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.listener = listener;
        }

        @Override
        public void terminate(Throwable reason) {
        }

        @Override
        public String getId() {
            return this.id;
        }

        @Override
        public String getHost() {
            return this.host;
        }

        @Override
        public int getPort() {
            return this.port;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public boolean execute(HttpRequestContext context) {
            this.requestsExecuted++;
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            context.getFuture().setSuccess(new Object(), response);
            return true;
        }

        public HttpConnectionListener getListener() {
            return listener;
        }

        public int getRequestsExecuted() {
            return requestsExecuted;
        }
    }

    public static class NeverAvailableHttpConnection extends AlwaysAvailableHttpConnection {

        public NeverAvailableHttpConnection(String id, String host, int port, HttpConnectionListener listener) {
            super(id, host, port, listener);
        }

        @Override
        public boolean isAvailable() {
            return false;
        }
    }
}
