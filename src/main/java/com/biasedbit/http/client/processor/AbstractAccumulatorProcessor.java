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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public abstract class AbstractAccumulatorProcessor<T>
        implements ResponseProcessor<T> {

    // internal vars --------------------------------------------------------------------------------------------------

    protected final    List<Integer> acceptedCodes;
    protected          ChannelBuffer buffer;
    protected volatile boolean       finished;
    protected          T             result;

    // constructors ---------------------------------------------------------------------------------------------------

    public AbstractAccumulatorProcessor() { acceptedCodes = null; }

    public AbstractAccumulatorProcessor(List<Integer> acceptedCodes) { this.acceptedCodes = acceptedCodes; }

    public AbstractAccumulatorProcessor(int... acceptedCodes) {
        this.acceptedCodes = new ArrayList<>(acceptedCodes.length);
        for (int acceptedCode : acceptedCodes) this.acceptedCodes.add(acceptedCode);
    }

    // ResponseProcessor ------------------------------------------------------------------------------------------

    @Override public boolean willProcessResponse(HttpResponse response)
            throws Exception {
        if (!isAcceptableResponse(response)) return false;

        // Content already present. Deal with it and bail out.
        if ((response.getContent() != null) && (response.getContent().readableBytes() > 0)) {
            result = convertBufferToResult(response.getContent());
            finished = true;
            return true;
        }

        // No content readily available
        long length = HttpHeaders.getContentLength(response, -1);
        if ((length > Integer.MAX_VALUE) || (length < -1)) {
            // Even though get/setContentLength works with longs, the value seems to be converted to an int so we need
            // to check if overflowed (-1 is no content length, < -1 is an overflowed length)
            finished = true;
            return false;
        }

        if (length == 0) {
            // No content
            finished = true;
            return false;
        }

        // If the response is chunked, then prepare the buffers for incoming data.
        if (response.isChunked()) {
            if (length < 0) {
                // No content header, but there may be content... use a dynamic buffer (not so good for performance...)
                buffer = ChannelBuffers.dynamicBuffer(2048);
            } else {
                // When content is zipped and autoInflate is set to true, the Content-Length header remains the same
                // even though the contents are expanded. Thus using a fixed size buffer would break with
                // ArrayIndexOutOfBoundsException
                buffer = ChannelBuffers.dynamicBuffer((int) length);
            }

            return true;
        }

        // Non-chunked request without content
        finished = true;
        return false;
    }

    @Override public void addData(ChannelBuffer content)
            throws Exception {
        if (!finished) buffer.writeBytes(content);
    }

    @Override public void addLastData(ChannelBuffer content)
            throws Exception {
        if (!finished) {
            if (content.readableBytes() > 0) buffer.writeBytes(content);
            result = convertBufferToResult(buffer);
            buffer = null;
            finished = true;
        }
    }

    @Override public T getProcessedResponse() { return result; }

    // protected helpers ----------------------------------------------------------------------------------------------

    protected abstract T convertBufferToResult(ChannelBuffer buffer);

    protected boolean isAcceptableResponse(HttpResponse response) {
        if (acceptedCodes == null) return true;
        else return acceptedCodes.contains(response.getStatus().getCode());
    }
}
