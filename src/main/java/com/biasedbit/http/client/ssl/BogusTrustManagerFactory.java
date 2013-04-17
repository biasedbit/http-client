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

import javax.net.ssl.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Bogus {@link javax.net.ssl.TrustManagerFactorySpi} which accepts any certificate
 * even if it is invalid.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev: 183008 $, $Date: 2008-11-18 20:44:38 -0500 (Tue, 18 Nov 2008) $
 */
public class BogusTrustManagerFactory
        extends TrustManagerFactorySpi {

    // public static methods ------------------------------------------------------------------------------------------

    public static TrustManager[] getTrustManagers() { return new TrustManager[] { DUMMY_TRUST_MANAGER }; }

    // TrustManagerFactorySpi -----------------------------------------------------------------------------------------

    @Override protected TrustManager[] engineGetTrustManagers() { return getTrustManagers(); }

    @Override protected void engineInit(KeyStore keystore)
            throws KeyStoreException { /* Unused */ }

    @Override protected void engineInit(ManagerFactoryParameters managerFactoryParameters)
            throws InvalidAlgorithmParameterException { /* Unused */ }

    // private classes ------------------------------------------------------------------------------------------------

    private static final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {

        // X509TrustManager -------------------------------------------------------------------------------------------

        @Override public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                throws CertificateException { /* always trust */ }

        @Override public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                throws CertificateException { /* always trust */ }

        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };
}
