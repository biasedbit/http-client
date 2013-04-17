http-client History
===================

## 1.1.0

#### April 22nd, 2013

* Change build system to gradle
* Add lombok to get rid of all the annoying getters and setters
* Update Netty to the latest 3.x branch
* Simplified the project by dropping all unnecessary factories (`HostContext`, `HttpRequestFuture`)
* Add specs with nearly 100% coverage
* Add mutation testing setup to gradle
* Internal refactorings and multiple bug fixes:

    * Fixed bug where connections were not being closed when exceptions where thrown ([#2](https://github.com/brunodecarvalho/http-client/issues/2))
    * Fixed bug where queued requests were not being down-counted when exceptions occurred ([#1](https://github.com/brunodecarvalho/http-client/issues/1))
    * Fixed a lot of other edge case bugs related to the connection-client relationship

* Renamed classes, dropping the "Http" prefix in most classes. Other than that, API remains pretty much the same:

    * `HttpRequestFuture` -> `RequestFuture`
    * `DefaultHttpConnection` -> `DefaultConnection`
    * `DefaultHttpConnectionFactory` -> `DefaultConnectionFactory`
    * `PipeliningHttpConnection` -> `PipeliningConnection`
    * `PipeliningHttpConnectionFactory` -> `PipeliningConnectionFactory`
    * `TimeoutManager` -> `TimeoutController`
    * `BasicTimeoutManager` -> `BasicTimeoutController`
    * `HashedWheelTimeoutManager` -> `HashedWheelTimeoutController`


## 1.0.0

#### 2011

* First release. Based on previous project called hotpotato.
