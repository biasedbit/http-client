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
import com.biasedbit.http.client.future.DefaultHttpRequestFuture;
import com.biasedbit.http.client.processor.DiscardProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class HostContextTestUtil {

    public static HttpRequestContext<Object> generateDummyContext(String host, int port, int timeout) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        return new HttpRequestContext<Object>(host, port, timeout, request, new DiscardProcessor(),
                                              new DefaultHttpRequestFuture<Object>(true));
    }

    public static HttpRequestContext<Object> generateDummyContext(String host, int port) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        return new HttpRequestContext<Object>(host, port, request, new DiscardProcessor(),
                                              new DefaultHttpRequestFuture<Object>(true));
    }
}
