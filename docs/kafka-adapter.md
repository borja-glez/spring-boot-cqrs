# Kafka Adapter Documentation

Module: `spring-boot-cqrs-kafka`  
Package: `com.borjaglez.cqrs.kafka`

The Kafka module provides distributed implementations of all three buses. When added to the classpath next to a starter, it auto-configures the topics, a producer and a consumer factory, the listener containers, the consumers and the Kafka buses. Each bus can be kept off Kafka separately.

## Table of Contents

- [Architecture](#architecture)
- [Choosing the Kafka Buses](#choosing-the-kafka-buses)
- [Topics and Naming](#topics-and-naming)
- [Bus Implementations](#bus-implementations)
- [Request/Reply](#requestreply)
  - [Generic results](#generic-results)
- [Partition Keys](#partition-keys)
- [Consumers and Consumer Groups](#consumers-and-consumer-groups)
- [Record Headers](#record-headers)
- [Producer and Consumer Settings](#producer-and-consumer-settings)
- [Observation](#observation)
- [Configuration Properties](#configuration-properties)
- [Auto-Configuration](#auto-configuration)
- [Running Next to the RabbitMQ Module](#running-next-to-the-rabbitmq-module)

## Architecture

```
Sending Service                                       Receiving Service
+----------------------+                              +------------------------+
| KafkaCommandBus      |---> {prefix}.commands ------>| KafkaCommandConsumer   |
| KafkaEventBus        |---> {prefix}.events -------->| KafkaEventConsumer     |
| KafkaQueryBus        |---> {prefix}.queries ------->| KafkaQueryConsumer     |
|                      |                              |   -> middleware chain  |
|                      |                              |   -> handler registry  |
| reply container      |<--- {prefix}.{app}.replies <-| (commands and queries) |
+----------------------+                              +------------------------+
```

There is one topic per bus type, shared by every service. Every service reads each topic with its own consumer group and skips the records it has no handler for. Commands and queries that expect an answer are replied to on the reply topic of the sending application.

Records have a `String` key (the [partition key](#partition-keys)) and a `byte[]` value produced by the `MessageSerializer` of the starter.

## Choosing the Kafka Buses

The Kafka buses are registered **in addition to** the in-process buses. `SpringCommandBus`, `SpringQueryBus` and the `springEventBus` stay `@Primary` (`CqrsAutoConfiguration`), so injecting `CommandBus`, `QueryBus` or `EventBus` gives the in-process bus. Inject the Kafka bus type to send over Kafka:

```java
@RestController
public class OrderController {

  private final KafkaCommandBus commandBus;
  private final KafkaQueryBus queryBus;

  public OrderController(KafkaCommandBus commandBus, KafkaQueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }
}
```

## Topics and Naming

### KafkaTopicNamingStrategy

```java
public interface KafkaTopicNamingStrategy {
    String topic(String logicalName);
    String replyTopic(String applicationName, String logicalName);
}
```

### DefaultKafkaTopicNamingStrategy

Prefixes every name with `cqrs.kafka.prefix`. With a blank prefix the logical name is used as is.

| Method | Pattern | Example (`prefix=cqrs`, `spring.application.name=orders-service`) |
|---|---|---|
| `topic("commands")` | `{prefix}.{commands.topic}` | `cqrs.commands` |
| `topic("events")` | `{prefix}.{events.topic}` | `cqrs.events` |
| `topic("queries")` | `{prefix}.{queries.topic}` | `cqrs.queries` |
| `replyTopic(app, "replies")` | `{prefix}.{app}.{replies.topic}` | `cqrs.orders-service.replies` |

The application name is read from `spring.application.name` (defaults to `cqrs-app` if not set). You can provide a custom `KafkaTopicNamingStrategy` bean to override the default naming.

### Topic creation

With `cqrs.kafka.auto-create-topics=true` (the default) the module declares one `NewTopic` bean per enabled bus (`cqrsCommandsTopic`, `cqrsEventsTopic`, `cqrsQueriesTopic`) and one for the reply topic (`cqrsRepliesTopic`), with the partitions and replicas configured for each. Spring Kafka's `KafkaAdmin`, which Spring Boot's Kafka auto-configuration provides, creates them at startup. Set the property to `false` when topics are managed outside the application.

The listener containers are created with `missingTopicsFatal=false`, so the application starts even if a topic does not exist yet.

## Bus Implementations

### KafkaCommandBus

Implements `CommandBus`. Sends commands to the commands topic.

- `dispatch(command)` -- publishes the record and returns once the send has completed; no answer is expected
- `dispatchAndWait(command)` -- request/reply in `WAIT` mode: returns when the handler has finished and rethrows its failure
- `dispatchAndReceive(command)` / `dispatchAndReceive(command, responseType)` -- request/reply in `REPLY` mode: returns the handler's result

### KafkaEventBus

Implements `EventBus`. Publishes events to the events topic.

- Has a **fallback** to the local `springEventBus`: if publishing throws a `RuntimeException` (for example, the broker is unreachable), the event is published in-process instead
- `publish(List<Event>)` publishes the events one by one

### KafkaQueryBus

Implements `QueryBus`. `ask(query)` and `ask(query, responseType)` use request/reply and return the handler's result.

### KafkaMessagePublisher

Shared publisher that wraps the module's `KafkaTemplate<String, byte[]>`:

- `publish(topic, message)` -- one-way publish; adds the message headers and the current `MessageContext` as context headers, and waits for the send to complete
- `publishReply(topic, correlationId, payload)` -- answers a request; a `null` result travels as an empty body without payload type and is read back as `null`
- `publishErrorReply(topic, correlationId, error)` -- answers a request with the handler's failure: the body is the exception message (or its class name when it has no message), with `cqrs.error=true` and `cqrs.error.type`

## Request/Reply

Request/reply is used by `dispatchAndWait`, `dispatchAndReceive` and `ask`. It is handled by `KafkaRequestReplyClient`:

1. A random UUID is generated as correlation id.
2. The request is sent with the headers `cqrs.correlation.id`, `cqrs.reply.topic` (the reply topic of this application) and `cqrs.request.mode` (`WAIT` or `REPLY`), plus the current `MessageContext` as context headers, like a one-way record, so the handler sees the caller's correlation id and context entries.
3. The caller blocks until a reply with the same correlation id arrives, or until `cqrs.kafka.replies.timeout` (default `30s`) expires.

On the receiving side the consumer publishes the reply to the topic named in `cqrs.reply.topic`, keyed by the correlation id:

| Outcome | Reply | What the caller gets |
|---|---|---|
| Handler returns a result (`REPLY`) | Serialized result with its payload type | The result, deserialized with `responseType` when given, otherwise with the payload type header |
| Handler returns `null` (`REPLY`) | Empty body, no payload type | `null` |
| Handler finishes (`WAIT`) | Empty string | Returns normally |
| Handler throws | Error reply (`cqrs.error=true`) | `RuntimeException("Remote handler error: <message>")` |
| No reply in time | -- | `RuntimeException("Timed out waiting for Kafka reply for <message name>")` |

### Generic results

The reply's `cqrs.payload.type` header names the runtime class of the result (`payload.getClass()`), which says nothing about its type arguments. Without a `responseType` the caller deserializes the reply as that class, so:

- A result whose class describes it fully (a record, a POJO, `String`, a boxed primitive) comes back as it was sent.
- A generic result comes back with untyped content: a `List<OrderDto>` is a list of `LinkedHashMap`, a `Result<OrderDto>` wrapper holds a `LinkedHashMap`. A list built with `List.of(...)` or `Stream.toList()` is named by its JDK-internal class (`java.util.ImmutableCollections$ListN`) and is read back as a list of maps as well.

Pass a `ParameterizedTypeReference` to get the element types back; it is used instead of the payload type header:

```java
List<OrderDto> orders =
    queryBus.ask(new ListOrders(), new ParameterizedTypeReference<List<OrderDto>>() {});

Result<OrderDto> placed =
    commandBus.dispatchAndReceive(
        new PlaceOrder("o-1"), new ParameterizedTypeReference<Result<OrderDto>>() {});
```

The local buses do not need it; they return the handler's object as it is.

### Reply topic and reply container

Each application reads its own reply topic, `{prefix}.{spring.application.name}.{replies.topic}`, created with `cqrs.kafka.replies.partitions` and `cqrs.kafka.replies.replicas`. The reply container (`cqrsKafkaReplyContainer`) runs with concurrency `1` and a consumer group unique to each application instance (`{app}.cqrs.replies.{random id}`), so every instance reads every reply and completes only the requests it is waiting for.

The request/reply infrastructure (`KafkaRequestReplyClient`, reply topic, reply container) is only created when commands or queries travel over Kafka. An application with `cqrs.kafka.commands.enabled=false` and `cqrs.kafka.queries.enabled=false` does not start it.

## Partition Keys

The record key of every command, event and query is computed by a `KafkaPartitionKeyStrategy`:

```java
public interface KafkaPartitionKeyStrategy {
    String partitionKey(KafkaMessageKind messageKind, Object message);
}
```

`DefaultKafkaPartitionKeyStrategy` follows `cqrs.kafka.partition-key.strategy`:

| Strategy | Record key |
|---|---|
| `MESSAGE_NAME` (default) | The message name from `MessageNamingStrategy` (for example `orders.1.command.order.create`) |
| `PAYLOAD_TYPE` | The fully qualified class name of the message |
| `NONE` | No key (`null`) |

With `MESSAGE_NAME` or `PAYLOAD_TYPE` all records of one message type share a key, so they land on the same partition and keep their order, and one consumer of the group handles them. To partition by business identity (an order id, say), provide your own `KafkaPartitionKeyStrategy` bean; it replaces the default:

```java
@Bean
KafkaPartitionKeyStrategy kafkaPartitionKeyStrategy() {
  return (kind, message) ->
      message instanceof OrderMessage order ? order.getOrderId() : null;
}
```

Replies are always keyed by their correlation id.

## Consumers and Consumer Groups

Each enabled bus has its own `ConcurrentMessageListenerContainer`:

| Container bean | Topic | Consumer group (default) | Concurrency |
|---|---|---|---|
| `cqrsKafkaCommandListenerContainer` | `{prefix}.{commands.topic}` | `{app}.cqrs.commands` | `cqrs.kafka.commands.concurrency` |
| `cqrsKafkaEventListenerContainer` | `{prefix}.{events.topic}` | `{app}.cqrs.events` | `cqrs.kafka.events.concurrency` |
| `cqrsKafkaQueryListenerContainer` | `{prefix}.{queries.topic}` | `{app}.cqrs.queries` | `cqrs.kafka.queries.concurrency` |

`{app}` is `spring.application.name`. Set `cqrs.kafka.<bus>.group-id` to use another group; a blank value keeps the default. Instances of the same application share the group and split the partitions; different applications have different groups and each receive every record.

### Consumer Classes

| Consumer | Handles | Behavior |
|---|---|---|
| `KafkaCommandConsumer` | Commands | Skips records whose payload type is not on the classpath or has no local handler (they belong to another service). Replies in `REPLY` and `WAIT` mode, including failures. |
| `KafkaEventConsumer` | Events | Skips records whose payload type is not on the classpath; otherwise dispatches to every local event handler. |
| `KafkaQueryConsumer` | Queries | Skips records whose payload type is not on the classpath or has no local handler. Always replies, including failures. |

Before invoking the handler every consumer rehydrates the `MessageContext` from the context headers and runs the full middleware chain (`BusMiddleware` beans) on this side. See [Middleware on remote buses](middleware.md#middleware-on-remote-buses).

A command dispatched with `dispatch` whose handler fails, or a failing event handler, is not answered: the exception is rethrown to the listener container and handled by its error handler. The module declares no retry or dead-letter topic.

A record without the `cqrs.payload.type` header is rejected with an `IllegalStateException`.

## Record Headers

| Header | Set on | Value |
|---|---|---|
| `cqrs.message.kind` | Requests and one-way records | `COMMAND`, `EVENT` or `QUERY` |
| `cqrs.message.name` | Requests and one-way records | Message name from `MessageNamingStrategy` |
| `cqrs.payload.type` | Requests, one-way records, replies with a result, error replies | Fully qualified class name of the payload |
| `cqrs.correlation.id` | Requests and replies | Correlation id of the request |
| `cqrs.reply.topic` | Requests | Reply topic of the sending application |
| `cqrs.request.mode` | Requests | `WAIT` or `REPLY` |
| `cqrs.error` | Error replies | `true` |
| `cqrs.error.type` | Error replies | Class name of the handler's exception |
| `{cqrs.context.header-prefix}{key}` | Requests and one-way records, when there is a current `MessageContext` (not on replies) | One header per `MessageContext` entry (for example `cqrs.context.correlationId`) |

The constants are in `KafkaMessageHeaders`.

## Producer and Consumer Settings

The module builds its own `cqrsKafkaProducerFactory`, `cqrsKafkaConsumerFactory` and `cqrsKafkaTemplate`. They start from the configuration of the factories Spring Boot creates from `spring.kafka.*` (Spring Boot 3 and Spring Boot 4 alike) and only replace:

- the key (de)serializer with `StringSerializer` / `StringDeserializer`;
- the value (de)serializer with `ByteArraySerializer` / `ByteArrayDeserializer`;
- `auto.offset.reset`, set to `earliest` when not configured.

Without Spring Boot's Kafka auto-configuration only `spring.kafka.bootstrap-servers` is read (default `localhost:9092`). Each of the three beans can be replaced by declaring a bean with the same name (`cqrsKafkaProducerFactory`, `cqrsKafkaConsumerFactory`) or type (`KafkaTemplate<String, byte[]>`).

## Observation

The module's template and listener containers follow the same switches as Spring Boot's own Kafka beans:

| Property | Default | Effect |
|---|---|---|
| `spring.kafka.template.observation-enabled` | `false` | Observes sends of `cqrsKafkaTemplate`; the current trace travels in the record headers |
| `spring.kafka.listener.observation-enabled` | `false` | Observes the command, event, query and reply containers; the sender's trace continues on the receiver |

These are the only sender-side observations of a Kafka dispatch: the CQRS middleware (`TracingMiddleware`, `MicrometerBusObservability`) runs on the receiver. See [Distributed Tracing](middleware.md#distributed-tracing).

## Configuration Properties

Defined in `KafkaCqrsProperties` (`cqrs.kafka.*`):

| Property | Type | Default | Description |
|---|---|---|---|
| `cqrs.kafka.enabled` | `boolean` | `true` | Master switch for all Kafka bus adapters. |
| `cqrs.kafka.prefix` | `String` | `"cqrs"` | Prefix for topic names. Blank means no prefix. |
| `cqrs.kafka.auto-create-topics` | `boolean` | `true` | Declares `NewTopic` beans for the enabled buses and the reply topic. |
| `cqrs.kafka.partition-key.strategy` | `MESSAGE_NAME` \| `PAYLOAD_TYPE` \| `NONE` | `MESSAGE_NAME` | Record key used by `DefaultKafkaPartitionKeyStrategy`. |
| `cqrs.kafka.replies.topic` | `String` | `"replies"` | Logical name of the reply topic (`{prefix}.{app}.{topic}`). |
| `cqrs.kafka.replies.partitions` | `int` | `1` | Partitions of the reply topic. |
| `cqrs.kafka.replies.replicas` | `short` | `1` | Replication factor of the reply topic. |
| `cqrs.kafka.replies.timeout` | `Duration` | `30s` | How long a request waits for its reply. |
| `cqrs.kafka.commands.enabled` | `boolean` | `true` | Whether commands use Kafka (bus, topic, consumer, container). |
| `cqrs.kafka.commands.topic` | `String` | `"commands"` | Logical name of the commands topic. |
| `cqrs.kafka.commands.partitions` | `int` | `3` | Partitions of the commands topic. |
| `cqrs.kafka.commands.replicas` | `short` | `1` | Replication factor of the commands topic. |
| `cqrs.kafka.commands.concurrency` | `int` | `1` | Concurrency of the command listener container. |
| `cqrs.kafka.commands.group-id` | `String` | `""` | Consumer group; blank means `{app}.cqrs.commands`. |
| `cqrs.kafka.events.enabled` | `boolean` | `true` | Whether events use Kafka. |
| `cqrs.kafka.events.topic` | `String` | `"events"` | Logical name of the events topic. |
| `cqrs.kafka.events.partitions` | `int` | `3` | Partitions of the events topic. |
| `cqrs.kafka.events.replicas` | `short` | `1` | Replication factor of the events topic. |
| `cqrs.kafka.events.concurrency` | `int` | `1` | Concurrency of the event listener container. |
| `cqrs.kafka.events.group-id` | `String` | `""` | Consumer group; blank means `{app}.cqrs.events`. |
| `cqrs.kafka.queries.enabled` | `boolean` | `true` | Whether queries use Kafka. |
| `cqrs.kafka.queries.topic` | `String` | `"queries"` | Logical name of the queries topic. |
| `cqrs.kafka.queries.partitions` | `int` | `3` | Partitions of the queries topic. |
| `cqrs.kafka.queries.replicas` | `short` | `1` | Replication factor of the queries topic. |
| `cqrs.kafka.queries.concurrency` | `int` | `1` | Concurrency of the query listener container. |
| `cqrs.kafka.queries.group-id` | `String` | `""` | Consumer group; blank means `{app}.cqrs.queries`. |

The module also reads `spring.application.name` (default `cqrs-app`), `cqrs.context.header-prefix` (default `cqrs.context.`), `spring.kafka.bootstrap-servers` and the two [observation](#observation) switches.

Example:

```yaml
spring:
  application:
    name: orders-service
  kafka:
    bootstrap-servers: localhost:9092

cqrs:
  kafka:
    prefix: acme
    partition-key:
      strategy: MESSAGE_NAME
    replies:
      timeout: 10s
    commands:
      concurrency: 3
    events:
      group-id: orders-projections
```

Kafka for events only (no command or query topic, no reply topic or reply container):

```yaml
cqrs:
  kafka:
    commands:
      enabled: false
    queries:
      enabled: false
```

## Auto-Configuration

The module provides four auto-configuration classes. All of them require `KafkaTemplate` on the classpath and `cqrs.kafka.enabled=true` (the default).

| Class | Additional condition | Creates |
|---|---|---|
| `KafkaCqrsAutoConfiguration` | -- | `KafkaTopicNamingStrategy`, `KafkaPartitionKeyStrategy`, `cqrsKafkaProducerFactory`, `cqrsKafkaConsumerFactory`, `cqrsKafkaTemplate`, `KafkaMessagePublisher`; when commands or queries are enabled, `KafkaRequestReplyClient`, `cqrsRepliesTopic` and `cqrsKafkaReplyContainer` |
| `KafkaCommandBusAutoConfiguration` | `CommandHandlerRegistry` bean + `cqrs.kafka.commands.enabled=true` | `KafkaCommandBus`, `KafkaCommandConsumer`, `cqrsCommandsTopic`, `cqrsKafkaCommandListenerContainer` |
| `KafkaEventBusAutoConfiguration` | `EventHandlerRegistry` bean + `cqrs.kafka.events.enabled=true` | `KafkaEventBus`, `KafkaEventConsumer`, `cqrsEventsTopic`, `cqrsKafkaEventListenerContainer` |
| `KafkaQueryBusAutoConfiguration` | `QueryHandlerRegistry` bean + `cqrs.kafka.queries.enabled=true` | `KafkaQueryBus`, `KafkaQueryConsumer`, `cqrsQueriesTopic`, `cqrsKafkaQueryListenerContainer` |

`KafkaTopicNamingStrategy`, `KafkaPartitionKeyStrategy`, `KafkaTemplate<String, byte[]>`, `KafkaMessagePublisher` and `KafkaRequestReplyClient` are `@ConditionalOnMissingBean` and can be replaced with your own bean. The topic beans are only created when `cqrs.kafka.auto-create-topics=true`.

## Running Next to the RabbitMQ Module

The Kafka and RabbitMQ modules can be on the classpath of the same application. Their beans have distinct names (`cqrsKafka*ListenerContainer` versus the RabbitMQ `cqrs*ListenerContainer`), so both start side by side.

Each transport has its own bus beans: inject `KafkaEventBus` to publish an event over Kafka and `RabbitMqCommandBus` to send a command over RabbitMQ, for example. A message is handled by the consumer of the transport it was sent on.

Both modules start consumers for every bus they enable. To keep a bus off Kafka, set `cqrs.kafka.<bus>.enabled=false`; `cqrs.rabbitmq.enabled=false` turns the whole RabbitMQ module off. See [rabbitmq-adapter.md](rabbitmq-adapter.md) for the RabbitMQ side.
