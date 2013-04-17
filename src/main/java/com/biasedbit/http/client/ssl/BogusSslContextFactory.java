/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.biasedbit.http.client.ssl;

import lombok.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.security.KeyStore;
import java.security.Security;

/**
 * Creates a bogus {@link javax.net.ssl.SSLContext}. A client-side context created by this factory accepts any
 * certificate even if it is invalid. A server-side context created by this factory sends a bogus certificate defined in
 * {@link com.biasedbit.http.client.ssl.BogusKeyStore}.
 *
 * You will have to create your context differently in a real world application.
 *
 * @author Trustin Lee (tlee@redhat.com)
 * @author <a href="https://github.com/jerjanssen">Jeremiah Janssen</a>
 * @author <a href="http://biasedbit.com">Bruno de Carvalho</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BogusSslContextFactory
        implements SslContextFactory {

    // constants ------------------------------------------------------------------------------------------------------

    private static final String                 PROTOCOL = "TLS";
    private static final BogusSslContextFactory INSTANCE = new BogusSslContextFactory();

    // internal vars --------------------------------------------------------------------------------------------------

    private final SSLContext serverContext = createServerContext();
    private final SSLContext clientContext = createClientContext();

    // public static methods ------------------------------------------------------------------------------------------

    public static BogusSslContextFactory getInstance() { return INSTANCE; }

    // SslContextFactory ----------------------------------------------------------------------------------------------

    @Override public SSLContext getServerContext() { return serverContext; }

    @Override public SSLContext getClientContext() { return clientContext; }

    // private static helpers -----------------------------------------------------------------------------------------

    @SneakyThrows(Exception.class) private static SSLContext createServerContext() {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) algorithm = "X509";

        // If you're on android, use BKS here instead of JKS
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(BogusKeyStore.asInputStream(), BogusKeyStore.getKeyStorePassword());

        // Set up key manager factory to use our key store
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, BogusKeyStore.getCertificatePassword());

        // Initialize the SSLContext to work with our key managers.
        SSLContext serverContext = SSLContext.getInstance(PROTOCOL);
        serverContext.init(kmf.getKeyManagers(), BogusTrustManagerFactory.getTrustManagers(), null);

        return serverContext;
    }

    @SneakyThrows(Exception.class) private static SSLContext createClientContext() {
        SSLContext clientContext = SSLContext.getInstance(PROTOCOL);
        clientContext.init(null, BogusTrustManagerFactory.getTrustManagers(), null);

        return clientContext;
    }
}
