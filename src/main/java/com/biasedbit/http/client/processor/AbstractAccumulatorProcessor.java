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
        implements HttpResponseProcessor<T> {

    // internal vars --------------------------------------------------------------------------------------------------

    protected final    List<Integer> acceptedCodes;
    protected          ChannelBuffer buffer;
    protected volatile boolean       finished;
    protected          T             result;

    // constructors ---------------------------------------------------------------------------------------------------

    public AbstractAccumulatorProcessor() {
        this.acceptedCodes = null;
    }

    public AbstractAccumulatorProcessor(List<Integer> acceptedCodes) {
        this.acceptedCodes = acceptedCodes;
    }

    public AbstractAccumulatorProcessor(int... acceptedCodes) {
        this.acceptedCodes = new ArrayList<Integer>(acceptedCodes.length);
        for (int acceptedCode : acceptedCodes) {
            this.acceptedCodes.add(acceptedCode);
        }
    }

    // HttpResponseProcessor ------------------------------------------------------------------------------------------

    @Override
    public boolean willProcessResponse(HttpResponse response) throws Exception {
        if ((this.acceptedCodes != null) && !this.acceptedCodes.contains(response.getStatus().getCode())) {
            return false;
        }

        // Content already present. Deal with it and bail out.
        if ((response.getContent() != null) && (response.getContent().readableBytes() > 0)) {
            this.result = this.convertBufferToResult(response.getContent());
            this.finished = true;
            return true;
        }

        // No content readily available
        long length = HttpHeaders.getContentLength(response, -1);
        if (length > Integer.MAX_VALUE) {
            this.finished = true;
            return false;
        }

        if (length == 0) {
            // No content
            this.finished = true;
            return false;
        }

        // If the response is chunked, then prepare the buffers for incoming data.
        if (response.isChunked()) {
            if (length == -1) {
                // No content header, but there may be content... use a dynamic buffer (not so good for performance...)
                this.buffer = ChannelBuffers.dynamicBuffer(2048);
            } else {
                // When content is zipped and autoInflate is set to true, the Content-Length header remains the same
                // even though the contents are expanded. Thus using a fixed size buffer would break with
                // ArrayIndexOutOfBoundsException
                this.buffer = ChannelBuffers.dynamicBuffer((int) length);
            }

            return true;
        }

        this.finished = true;
        return false;
    }

    @Override
    public void addData(ChannelBuffer content) throws Exception {
        if (!this.finished) {
            this.buffer.writeBytes(content);
        }
    }

    @Override
    public void addLastData(ChannelBuffer content) throws Exception {
        if (!this.finished) {
            this.buffer.writeBytes(content);
            this.result = this.convertBufferToResult(this.buffer);
            this.buffer = null;
            this.finished = true;
        }
    }

    @Override
    public T getProcessedResponse() {
        return this.result;
    }

    // protected helpers ----------------------------------------------------------------------------------------------

    protected abstract T convertBufferToResult(ChannelBuffer buffer);
}
