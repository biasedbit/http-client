package com.biasedbit.http.client.processor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * HTTP response body consumer.
 * <p/>
 * A {@code ResponseProcessor} is a class the is fed with the contents of the HTTP response body and returns the
 * result of converting that body into an object.
 * <p/>
 * A response processor is given the choice to decide whether it wants to process the response body for a given request
 * by having its {@link #willProcessResponse(org.jboss.netty.handler.codec.http.HttpResponse)} called prior to any of
 * the data appending methods.
 * <p/>
 * An example would be a processor that only wants to consume the body if the content type is 'text/plain'.
 * <p/>
 * <pre class="code">
 * boolean willProcessResponse(HttpResponse response) {
 *     if ("text/plain".equals(HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE))) {
 *         return true;
 *      }
 *      return false;
 * }</pre>
 * Processors may also choose to perform some kind of setup when {@link
 * #willProcessResponse(org.jboss.netty.handler.codec.http.HttpResponse)} is called:
 * <pre class="code">
 * boolean willProcessResponse(HttpResponse response) {
 *     if ("text/plain".equals(HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE))) {
 *         this.processPlainText = true;
 *         return true;
 *      }
 *     if ("text/html".equals(HttpHeaders.getHeader(response, HttpHeaders.Names.CONTENT_TYPE))) {
 *         this.processPlainText = false;
 *         return true;
 *      }
 *      return false;
 * }</pre>
 * <p/>
 * Since the body can be split into {@code org.jboss.netty.handler.codec.http.HttpChunk}s, methods {@link
 * #addData(org.jboss.netty.buffer.ChannelBuffer)} and {@link #addLastData(org.jboss.netty.buffer.ChannelBuffer)}
 * provide a simple way to append data that is not fully available when the request headers are read.
 * <div class="note">
 * <div class="header">Note:</div>
 * A {@code ResponseProcessor} is tipically stateful. Unless you're using a completely stateless response processor
 * such as {@link DiscardProcessor}, you <strong>must</strong> provide a different instance for each {@link
 * com.biasedbit.http.client.HttpClient#execute(String, int, org.jboss.netty.handler.codec.http.HttpRequest,
 * ResponseProcessor)} call.
 * </div>
 *
 * @see DiscardProcessor
 * @see BodyAsStringProcessor
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface ResponseProcessor<T> {

    /**
     * Query to determine if this processor has the necessary conditions to process this response.
     * <p/>
     * More complex stateful response processors may use some of the information on the headers to determine how they
     * will parse the data provided.
     *
     * @param response The HTTP response received.
     *
     * @return {@code true} if this processor will process the provided response, {@code false} otherwise.
     *
     * @throws Exception Thrown on underlying processing exception.
     */
    boolean willProcessResponse(HttpResponse response)
            throws Exception;

    /**
     * Append data to the processor's buffer.
     *
     * @param content Data received as part of the HTTP response body.
     *
     * @throws Exception Thrown on underlying processing exception.
     */
    void addData(ChannelBuffer content)
            throws Exception;

    /**
     * Append the last piece of data to the processor's buffer.
     * <p/>
     * The processor may choose to perform its operations on the buffer and transforming it the object to return once
     * this last piece of data is received. Alternatively, it can also perform that operation only when {@link
     * #getProcessedResponse()} is called.
     *
     * @param content Data received as part of the HTTP response body.
     *
     * @throws Exception Thrown on underlying processing exception.
     */
    void addLastData(ChannelBuffer content)
            throws Exception;

    /**
     * Returns the result of processing the HTTP response body. Result varies according to implementation.
     * <p/>
     * <div class="note">
     * <div class="header">Note:</div>
     * Implementations may choose to return non-{@code null} results
     * when no data is consumed so {@link com.biasedbit.http.client.future.RequestFuture#setSuccess(Object,
     * org.jboss.netty.handler.codec.http.HttpResponse)} should always be called with {@code getProcessedResponse()}
     * rather than {@code null}.
     * </div>
     *
     * @return The result of processing the HTTP response body or some default value in case of failure
     * (can be {@code null}).
     */
    T getProcessedResponse();
}
