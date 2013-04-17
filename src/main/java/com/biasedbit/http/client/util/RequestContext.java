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

import com.biasedbit.http.client.future.DataSinkListener;
import com.biasedbit.http.client.future.DefaultRequestFuture;
import com.biasedbit.http.client.processor.ResponseProcessor;
import lombok.Getter;
import lombok.Setter;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

import static com.biasedbit.http.client.util.Utils.ensureValue;

/**
 * State holder context for a request.
 * <p/>
 * This structure is passed between the {@link com.biasedbit.http.client.HttpClient} and the {@link
 * com.biasedbit.http.client.connection.Connection} and associates a
 * {@link org.jboss.netty.handler.codec.http.HttpRequest} to a {@link com.biasedbit.http.client.processor.ResponseProcessor} and a
 * {@link com.biasedbit.http.client.future.RequestFuture}.
 * <p/>
 * It also contains other information such as the host address to which this request was originally inteded for,
 * as well as its port and the timeout for the HTTP request/response operation to complete.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class RequestContext<T> {

    // properties -----------------------------------------------------------------------------------------------------

    @Getter private final String               host;
    @Getter private final int                  port;
    @Getter private final int                  timeout;
    @Getter private final HttpRequest          request;
    @Getter private final ResponseProcessor<T> processor;

    @Getter private final DefaultRequestFuture<T> future = new DefaultRequestFuture<>();

    @Getter @Setter private DataSinkListener dataSinkListener;

    // constructors ---------------------------------------------------------------------------------------------------

    public RequestContext(String host, int port, int timeout, HttpRequest request, ResponseProcessor<T> processor) {
        ensureValue(host != null, "Host cannot be null");
        ensureValue(port > 0 && port <= 65536, "Invalid port: " + port);
        ensureValue(request != null, "HttpRequest cannot be null");
        ensureValue(processor != null, "ResponseProcessor cannot be null");

        this.host = host;
        this.port = port;
        this.timeout = timeout < 0 ? 0 : timeout;
        this.request = request;
        this.processor = processor;
    }

    // interface ------------------------------------------------------------------------------------------------------

    /**
     * Determines (based on request method) if a request is idempotent or not, based on recommendations of the RFC.
     * <p/>
     * Idempotent requests: GET, HEAD, PUT, DELETE, OPTIONS, TRACE
     * Non-idempotent requests: POST, PATCH, CONNECT
     * <p/>
     * More info: http://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html
     *
     * @return true if request is idempotent, false otherwise.
     */
    public boolean isIdempotent() {
        return !(request.getMethod().equals(HttpMethod.POST) ||
                 request.getMethod().equals(HttpMethod.PATCH) ||
                 request.getMethod().equals(HttpMethod.CONNECT));
    }

    // object overrides -----------------------------------------------------------------------------------------------

    @Override public String toString() {
        return new StringBuilder()
                .append(request.getMethod()).append(' ')
                .append(request.getUri()).append(" (")
                .append(host).append(':')
                .append(port).append(')').append("@").append(Integer.toHexString(hashCode())).toString();
    }
}
