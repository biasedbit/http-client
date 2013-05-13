HTTP client
===========

This project is a Java high performance and throughput oriented HTTP client library, with support for HTTP 1.1 pipelining.
It was developed mostly towards server-side usage, where speed and low resource usage are the key factors, but can be used to build client applications as well.

Built on top of Netty and designed for high concurrency scenarios where multiple threads can use the same instance of a client without any worries for external or internal synchronization, it helps you reduce initialization and/or preparation times and resource squandering.
Among many small optimizations, connections are reused whenever possible, which results in a severe reduction of total request execution times by cutting connection establishment overhead.


### Version 1.1 is nearly ready

Be sure to check the [change log](CHANGES.md). The user-facing API has remained pretty much the same apart from some class renaming &mdash; the transition should be smooth.


## Dependencies

* JDK 1.7
* [Netty 3.5](http://netty.io/downloads/)
* [Lombok 1.6](http://projectlombok.org)


## Usage examples

### Synchronous mode

This example contains all the steps to execute a request, from creation to cleanup.
This is the synchronous mode, which means that the calling thread will block until the request completes.

```java
// Create & initialise the client
HttpClient client = new DefaultHttpClient();
client.init();

// Setup the request
HttpRequest request = new DefaultHttpRequest(HTTP_1_0, GET, "/");

// Execute the request, turning the result into a String
RequestFuture<String> future = client.execute("biasedbit.com", 80, request, new BodyAsStringProcessor());
future.awaitUninterruptibly();
// Print some details about the request
System.out.println(future);
    
// If response was >= 200 and <= 299, print the body (a String)
if (future.hasSuccessfulResponse()) System.out.println(future.getProcessedResult());

// Cleanup
client.terminate();
```

### Asynchronous mode (recommended)

In asynchronous mode, an event listener is attached to the object returned by the http client when a request execution is submitted. Attaching this listener allows the programmer to define some computation to occur when the request finishes.

```java
// Execute an asynchronous request, JDK 8 syntax
RequestFuture<String> future = client.execute("biasedbit.com", 80, request, new BodyAsStringProcessor());
future.addListener((future) -> {
        System.out.println(future);
        if (future.hasSuccessfulResponse()) System.out.println(future.getProcessedResult());
        client.terminate();
    }
});

// Execute an asynchronous request
RequestFuture<String> future = client.execute("biasedbit.com", 80, request, new BodyAsStringProcessor());
future.addListener(new RequestFutureListener<String>() {
    @Override public void operationComplete(RequestFuture<String> future)
            throws Exception {
        System.out.println(future);
        if (future.hasSuccessfulResponse()) System.out.println(future.getProcessedResult());
        client.terminate();
    }
});
```

> **NOTE:** you should **never** perform non-CPU bound operations in the listeners.

### Integration with IoC containers

IoC compliance was paramount when developing this library.

Here's a simple example of how to configure a client in [Spring](http://www.springsource.org/):

```xml
<bean id="httpClient" class="com.biasedbit.http.client.DefaultHttpClient"
      init-method="init" destroy-method="terminate">
  <property ... />
</bean>
```

Or using a client factory, in case you want multiple clients:

```xml
<bean id="httpClientFactory" class="com.biasedbit.http.client.factory.DefaultHttpClientFactory">
  <property ... />
</bean>
```

`HttpClientFactory` instances will configure each client produced exactly how they were configured - they have the same options as (or more than) the `HttpClient`s they generate.
Instead of having some sort of configuration object, you configure the factory and then call `createClient()` in it to obtain a pre-configured client.

You can also create a client for a component, based on a predefined factory:

```xml
<bean id="someBean" class="com.biasedbit.SomeComponent">
  <property name="httpClient">
    <bean factory-bean="httpClientFactory" factory-method="createClient" />
  </property>
  <property ... />
</bean>
```

Note that you can accomplish the same effect as the factory example by simply using an abstract definition of a `HttpClient` bean and then using Spring inheritance.


## Test coverage status

This project has a battery of unit and integration tests created with [Spock Framework](https://github.com/spockframework/spock).

The current coverage, as reported by IntelliJ IDEA is around **92%**. Keep in mind this project uses Lombok, so there is some auto-generated code (getters & setters, constructors) that is not tested.

This project is also configured with [pitest](http://pitest.org) through [gradle-pitest-plugin](http://gradle-pitest-plugin.solidsoft.info/). The coverage reported by pitest is **81%**, with **55%** mutation coverage.

To run the mutation test target and check the report, run the following:

```bash
$ gradle pitest
$ open build/reports/pitest/<most recent date>/index.html
```

> The difference in coverage reported by IDEA and pitest's reports is due to the use of Lombok (IDEA reports coverage on original source code and pitest reports coverage on de-lombok'd (expanded) code.


## License

This project is licensed under the [Apache License version 2.0](http://www.apache.org/licenses/LICENSE-2.0) as published by the Apache Software Foundation.
