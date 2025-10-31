# Mbs SDK
----------------
Mbs SDK is a client library that enables easier integration with the Mbs web interface. SDK provides entities (messages sent to and recevived from the server) as well as websocket connection handling.

### Installation
SDK can be obtained from [maven repository](https://mvnrepository.com/artifact/com.sportradar.mbs.sdk/mbs-sdk).

### Usage

#### Initialize the SDK

Create [MbsSdkConfig](https://github.com/sportradar/MbsSdkJava/blob/main/src/sdk/src/main/java/com/sportradar/mbs/sdk/MbsSdkConfig.java) configuration object with the values provided by Sportradar:

```java
MbsSdkConfig config = new MbsSdkConfig(
        wsServer, authServer, authClientId, authClientSecret, authAudience, operatorId);
```

Use the `config` to create [MbsSdk](https://github.com/sportradar/MbsSdkJava/blob/main/src/sdk/src/main/java/com/sportradar/mbs/sdk/MbsSdk.java) instance and establish a connection to the server:

```java
MbsSdk mbsSdk = new MbsSdk(config);
mbsSdk.connect();
```

The SDK is threadsafe and a single instance can be used for the entire application lifespan.

#### Use the SDK

Operations that are supported by the SDK are grouped into protocols, eg: all ticket operations are available under [TicketProtocol](https://github.com/sportradar/MbsSdkJava/blob/main/src/sdk/src/main/java/com/sportradar/mbs/sdk/protocol/TicketProtocol.java).  

The SDK instances exposes all the protocols through getter methods, eg:

```java
mbsSdk.getTicketProtocol();
```

Each operation (method) exposed by the protocol is generally of a request/response form. Each method accepts its Request object (eg: TicketRequest, CancelRequest, ...) and returns matching Response object (eg: TicketResponse, CancelResponse, ...) and each method is available in a blocking and async variant:

```java
// create appropriate request
OperationRequest request = new OperationRequest();
// blocking
OperationResponse response = mbsSdk.getProtocol().executeOperation(request);
// async
CompletableFuture<OperationResponse> future = mbsSdk.getProtocol().executeOperationAsync(request);
```

Each entity provides a companion builder object which is created by invoking static method `newBuilder()`. Builders enable fluent creation of new instances.  

Example of ticket place operation:

```java
TicketRequest request = TicketRequest.newBuilder()
    .setContext(TicketContext.newBuilder()
        .setLimitId(1234567890)
        .set... // omitted for brevity
        .build())
    .setTicketId("ticketId-1234567890")
    .set... // omitted for brevity
    .build();
TicketResponse response = mbsSdk.getTicketProtocol().sendTicket(request);
```

#### Close the SDK

When MbsSdk instance is not needed anymore, invoke `close()` to release the resources:

```java
mbsSdk.close();
```

### Further reading

[Betradar documentation](https://docs.betradar.com)
