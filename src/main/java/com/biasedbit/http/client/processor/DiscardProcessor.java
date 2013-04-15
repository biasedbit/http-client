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

/**
 * {@link ResponseProcessor} implementation that always discards the response body
 * ({@link #willProcessResponse(org.jboss.netty.handler.codec.http.HttpResponse)} always returns {@code false}).
 * <p/>
 * Always returns {@code null} when  {@link #getProcessedResponse()} is called and performs no action when
 * {@link #addData(org.jboss.netty.buffer.ChannelBuffer)} or {@link #addLastData(org.jboss.netty.buffer.ChannelBuffer)}
 * are called.
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DiscardProcessor
        extends TypedDiscardProcessor<Object> { }
