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

package com.biasedbit.http.client.ssl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Based on jerjanssen's ssl branch of hotpotato.
 *
 * @author <a href="https://github.com/jerjanssen">Jeremiah Janssen</a>
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DefaultSslContextFactory
        implements SslContextFactory {

    // internal vars --------------------------------------------------------------------------------------------------

    private final SSLContext serverContext;
    private final SSLContext clientContext;

    // SslContextFactory ----------------------------------------------------------------------------------------------

    @Override public SSLContext getClientContext() { return clientContext; }

    @Override public SSLContext getServerContext() { return serverContext; }

    // public classes -------------------------------------------------------------------------------------------------

    public static class Builder {

        // internal vars ----------------------------------------------------------------------------------------------

        private InputStream keyAsInputStream;
        private String      algorithm;
        private String      protocol;
        private KeyStore    store;
        private String      keyStorePassword;
        private String      certificatePassword;

        // interface --------------------------------------------------------------------------------------------------

        public Builder setAlgorithm(final String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder setProtocol(final String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder setKey(final InputStream key) {
            if (null != key) keyAsInputStream = key;
            else setKey((byte[]) null);

            return this;
        }

        public Builder setKey(final byte[] key) {
            keyAsInputStream = new ByteArrayInputStream((null != key) ? key : new byte[]{});
            return this;
        }

        public Builder setKeyStorePassword(final String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            return this;
        }

        public Builder setCertificatePassword(final String certificatePassword) {
            this.certificatePassword = certificatePassword;
            return this;
        }

        public Builder setKeyStore(final KeyStore store) {
            this.store = store;
            return this;
        }

        public DefaultSslContextFactory build()
                throws Exception {
            if (algorithm == null) algorithm = "SunX509";
            if (protocol == null) protocol = "TLSv1";
            if (store == null) store = KeyStore.getInstance("JKS");

            // Load our keystore from disk..
            store.load(keyAsInputStream, (keyStorePassword == null) ? null : keyStorePassword.toCharArray());

            KeyManagerFactory keyMgrFactory = KeyManagerFactory.getInstance(algorithm);
            keyMgrFactory.init(store, (certificatePassword == null) ? null : certificatePassword.toCharArray());

            TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
            trustMgrFactory.init(store);

            SSLContext serverContext = SSLContext.getInstance(protocol);
            SSLContext clientContext = SSLContext.getInstance(protocol);

            serverContext.init(keyMgrFactory.getKeyManagers(), trustMgrFactory.getTrustManagers(), new SecureRandom());
            clientContext.init(keyMgrFactory.getKeyManagers(), trustMgrFactory.getTrustManagers(), new SecureRandom());

            return new DefaultSslContextFactory(serverContext, clientContext);
        }
    }
}
