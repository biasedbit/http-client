package com.biasedbit.http.client.future;

import com.biasedbit.http.client.connection.HttpConnection;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public interface MutableRequestFuture<T>
        extends HttpRequestFuture<T> {

    void attachConnection(HttpConnection connection);

    boolean finishedSuccessfully(T processedResult, HttpResponse response);

    boolean failedWithCause(Throwable cause);

    boolean failedWithCause(Throwable cause, HttpResponse response);
}
