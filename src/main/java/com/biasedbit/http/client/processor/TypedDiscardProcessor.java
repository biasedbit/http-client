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

package com.biasedbit.http.client.processor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * Same as {@link DiscardProcessor} except that it has a type parameter to avoid compiler warnings.
 *
 * @see com.biasedbit.http.client.processor.DiscardProcessor
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class TypedDiscardProcessor<T>
        implements ResponseProcessor<T> {

    // constants ------------------------------------------------------------------------------------------------------

    private static final TypedDiscardProcessor<Object> CACHED = new TypedDiscardProcessor<>();

    // public static methods ------------------------------------------------------------------------------------------

    public static TypedDiscardProcessor<Object> getInstance() { return CACHED; }

    // ResponseProcessor ------------------------------------------------------------------------------------------

    @Override public boolean willProcessResponse(HttpResponse response) { return false; }

    @Override public void addData(ChannelBuffer content)
            throws Exception { }

    @Override public void addLastData(ChannelBuffer content)
            throws Exception { }

    @Override public T getProcessedResponse() { return null; }
}
