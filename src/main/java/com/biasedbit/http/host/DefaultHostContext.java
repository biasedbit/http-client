/*
 * Copyright 2012 Bruno de Carvalho
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

package com.biasedbit.http.host;

/**
 * The default and simplest implementation of the HostContext interface.
 *
 * This class is designed for extension as it contains boilerplate code that all implementations of HostContext would
 * surely have as well. Typically, an implementation would only override the drainQueue() strategy
 *
 * @author <a href="http://biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHostContext extends AbstractHostContext {

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHostContext(String host, int port, int maxConnections) {
        super(host, port, maxConnections);
    }
}
