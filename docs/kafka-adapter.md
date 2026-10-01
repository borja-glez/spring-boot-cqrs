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
- [Retry and Dead-Letter Topics](#retry-and-dead-letter-topics)
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

    default String deadLetterTopic(String applicationName, String logicalName) {
        return topic(applicationName + "." + logicalName + ".dlt");
    }
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
| `deadLetterTopic(app, "events")` | `{prefix}.{app}.{events.topic}.dlt` | `cqrs.orders-service.events.dlt` |

The application name is read from `spring.application.name` (defaults to `cqrs-app` if not set). You can provide a custom `KafkaTopicNamingStrategy` bean to override the default naming.

### Topic creation

With `cqrs.kafka.auto-create-topics=true` (the default) the module declares one `NewTopic` bean per enabled bus (`cqrsCommandsTopic`, `cqrsEventsTopic`, `cqrsQueriesTopic`) one for the reply topic (`cqrsRepliesTopic`) and, while dead-lettering is enabled, one dead-letter topic per enabled bus (`cqrsCommandsDeadLetterTopic`, `cqrsEventsDeadLetterTopic`, `cqrsQueriesDeadLetterTopic`), with the partitions and replicas configured for each. Spring Kafka's `KafkaAdmin`, which Spring Boot's Kafka auto-configuration provides, creates them at startup. Set the property to `false` when topics are managed outside the application.

The listener containers are created with `missingTopicsFatal=false`, so the application starts even if a topic does not exist yet.

## Bus Implementations

### Middleware before sending

Every send of the three buses (`dispatch`, `dispatchAndWait`, `dispatchAndReceive`, `ask`, `publish`) first runs the `BusMiddleware` beans that declare `DispatchPhase.OUTBOUND`, in the sending application; the consumer of the receiving application runs those that declare `INBOUND`. Of the built-in middleware, `ContextPropagationMiddleware` and `CommandValidationInterceptor` run on the sender: an invalid command throws `ConstraintViolationException` in the caller and is not sent (before 0.4.0 the caller got `RuntimeException("Remote handler error: ...")`), and a record sent without a `MessageContext` carries the `cqrs.context.correlationId` generated by the sender. Tracing, metrics and retry run only on the receiver. A middleware that throws stops the send: nothing is sent. See [Middleware on Remote Buses](middleware.md#middleware-on-remote-buses), which also has the upgrade note for 0.4.0.

The auto-configured buses receive every `BusMiddleware` bean; a bus built by hand runs outbound middleware only when it is given the list (the constructors that take a `List<BusMiddleware>`).

### KafkaCommandBus

Implements `CommandBus`. Sends commands to the commands topic.

- `dispatch(command)` -- publishes the record and returns once the send has completed; no answer is expected
- `dispatchAndWait(command)` -- request/reply in `WAIT` mode: returns when the handler has finished and rethrows its failure
- `dispatchAndReceive(command)` / `dispatchAndReceive(command, responseType)` -- request/reply in `REPLY` mode: returns the handler's result

### KafkaEventBus

Implements `EventBus`. Publishes events to the events topic.

- `publish(event)` returns only after the broker has acknowledged the record. If the send fails (for example, the broker is unreachable), the exception thrown by `KafkaMessagePublisher.publish` is rethrown unchanged and the event is **not** delivered in-process: there is no local fallback. Local handlers receive a published event through this application's own event consumer group
- `publish(List<Event>)` publishes the events one by one, in order, and stops at the first failure, rethrowing it. The events before the failing one have been sent; the failing one and those after it have not. There is no batching or rollback
- It is not wrapped by the transactional event bus: a caller inside a transaction publishes before the commit. For reliable publication consistent with the database, use the Outbox Pattern (see [Transactional event publishing](../README.md#transactional-event-publishing))

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

Consumers match records by name and fall back to the payload type, so a message without `@CqrsMessage` is still delivered; its name, and therefore its `MESSAGE_NAME` key, is then the kebab-case simple class name (`order-placed`), which can collide between services and changes when the class is renamed. Annotate the messages that other services consume so they get a stable, service-qualified name (see [@CqrsMessage](core.md#cqrsmessage)).

Kafka only keeps order within a partition. With `MESSAGE_NAME` or `PAYLOAD_TYPE` all records of one message type share a key, so they keep their order among themselves, but two messages of different types about the same order (an `OrderPlaced` and an `OrderCancelled`, say) usually land on different partitions and can be consumed in any order.

### Keying by entity with `KeyedMessage`

A command or event that implements `com.borjaglez.cqrs.KeyedMessage` declares its own key, and the default strategy uses it before looking at `cqrs.kafka.partition-key.strategy`:

```java
@CqrsMessage(service = "orders", module = "order", name = "order-placed")
public class OrderPlaced extends Event implements KeyedMessage {
  private UUID orderId;

  @Override
  public String messageKey() {
    return orderId.toString();
  }
}
```

- Every record with the same key, whatever its message type, goes to the same partition of the topic and is consumed in publication order. The guarantee is per key and per topic: commands and events travel on different topics.
- A message that does not implement `KeyedMessage`, or returns a `null` or blank key, is keyed by the configured strategy, so nothing changes for existing messages. The default stays `MESSAGE_NAME`.
- Queries ignore a declared key: they have no ordering need.
- Requests sent with `dispatchAndReceive` or `ask` follow the same rule as one-way records.
- The declared key is also sent as the `cqrs.message.key` header (only when there is one), so consumers can read it without deserializing the payload.

> **Switching keys:** records keep the partition they were written to. When an existing message starts declaring a key (or changes it), the records of one entity published before and after the deployment can sit on different partitions for as long as the older ones are not consumed, so for that window their relative order is not guaranteed. Let the topic drain, or tolerate reordering for that window.

### Custom strategy

To compute keys in another way, provide your own `KafkaPartitionKeyStrategy` bean; it replaces the default, including its `KeyedMessage` handling (`KafkaMessageKeys.declaredKey(kind, message)` returns the declared key if you want to keep it):

```java
@Bean
KafkaPartitionKeyStrategy kafkaPartitionKeyStrategy() {
  return (kind, message) ->
      message instanceof OrderMessage order ? order.getOrderId() : null;
}
```

The `cqrs.message.key` header is added from the declared key regardless of the strategy.

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
| `KafkaCommandConsumer` | Commands | Skips records whose name and payload type are both unknown locally, or that have no local handler (they belong to another service). Replies in `REPLY` and `WAIT` mode, including failures. |
| `KafkaEventConsumer` | Events | Skips records whose name and payload type are both unknown locally; otherwise dispatches to every local event handler. |
| `KafkaQueryConsumer` | Queries | Skips records whose name and payload type are both unknown locally, or that have no local handler. Always replies, including failures. |

Before invoking the handler every consumer rehydrates the `MessageContext` from the context headers and runs the `BusMiddleware` beans that declare `DispatchPhase.INBOUND` (by default, every middleware). See [Middleware on remote buses](middleware.md#middleware-on-remote-buses).

A command dispatched with `dispatch` whose handler fails, or a failing event handler, is not answered: the exception is rethrown to the listener container, which retries the record and then publishes it to the application's dead-letter topic (see [Retry and Dead-Letter Topics](#retry-and-dead-letter-topics)).

A record whose name is unknown locally and that has no `cqrs.payload.type` header, or whose payload cannot be deserialized into that type, is rejected with an `UnprocessableRecordException` (an `IllegalStateException`), which is never retried.

## Retry and Dead-Letter Topics

The command, event and query listener containers share one error-handling policy, configured under `cqrs.kafka.error-handling.*`:

1. A record whose processing throws is delivered again, up to `max-attempts` deliveries in total (default `3`: the first one plus two retries). The container seeks back to the record, so the partition waits during the back-off and records keep their order.
2. The wait after the n-th failed delivery is `min(max-interval, initial-interval * multiplier^(n - 1))`: `1s`, then `2s`, capped at `10s` with the defaults. It is computed by the core `BackoffStrategy.exponential`, the type behind the `RetryMiddleware` (see [Retry Properties](configuration.md#retry-properties)).
3. Once the attempts are exhausted, the record is published to the dead-letter topic of this application and bus, `{prefix}.{app}.{bus-topic}.dlt` (for example `cqrs.orders-service.events.dlt`), and the container moves on. With `dead-letter.enabled=false` the record is logged and skipped instead.
4. An `UnprocessableRecordException` (no payload type header, payload that cannot be deserialized) and Spring Kafka's own fatal exceptions (`DeserializationException`, `ClassCastException`, ...) are not retried: the record goes to the dead-letter topic after its first delivery.

**Why per application.** The commands, events and queries topics are shared by every service, each reading them with its own consumer group. A record that failed in one service usually succeeded in the others, so a shared `<topic>-dlt` would mix the failures of every service, and replaying it to the source topic would deliver the record again to services that had already processed it. The dead-letter topic therefore belongs to the application whose handler failed, like the per-application dead-letter queues of the RabbitMQ module.

**What a dead-lettered record carries.** The original key, value and headers (`cqrs.payload.type`, `cqrs.message.*`, context headers, ...), the `kafka_dlt-*` headers added by Spring Kafka's `DeadLetterPublishingRecoverer` (original topic, partition, offset, timestamp, exception class, message and stack trace), and the failure headers shared with the RabbitMQ dead-letter queues, so one replay design fits both transports:

| Header | Value |
|---|---|
| `cqrs.error.type` | Class name of the handler's exception, without the listener container's wrapper |
| `cqrs.error.message` | Exception message truncated to 1000 characters; absent when the exception has no message |
| `cqrs.error.attempts` | Number of deliveries made, as a decimal string (`1` for a record that is not retried) |
| `cqrs.error.timestamp` | ISO-8601 instant at which the record was dead-lettered |

The dead-letter record is published with no explicit partition, so the dead-letter topic does not need as many partitions as the source topic. The containers set `deliveryAttemptHeader`, so records also carry Spring Kafka's `kafka_deliveryAttempt` header.

**What is not dead-lettered.** Request/reply commands and queries whose handler fails are answered with an error reply (see [Request/Reply](#requestreply)) and are neither retried nor dead-lettered. The reply container has no retry or dead-letter topic: a reply nobody waits for is dropped. Records whose name and payload type are both unknown locally are skipped, as before: they belong to other services.

**Using your own error handler.** Declare a `CommonErrorHandler` bean (for example a `DefaultErrorHandler` with another recoverer) and it is applied to the three containers instead; `cqrs.kafka.error-handling.*` is then ignored, but the dead-letter `NewTopic` beans are still declared while `dead-letter.enabled=true`. If the application has several `CommonErrorHandler` beans, the one named `cqrsKafkaErrorHandler` wins, then a `@Primary` one; without either, the module's own handler is used. The module's handler is deliberately not a bean: Spring Boot applies a unique `CommonErrorHandler` bean to its `@KafkaListener` container factory, and the CQRS dead-letter publishing is not meant for the application's own listeners.

**With the `RetryMiddleware`.** `cqrs.retry.*` runs inside each delivery, so the handler runs up to `cqrs.retry.max-attempts x cqrs.kafka.error-handling.max-attempts` times before the record is dead-lettered. Keep the total back-off of one record well below the consumer's `max.poll.interval.ms` (5 minutes by default).

## Record Headers

| Header | Set on | Value |
|---|---|---|
| `cqrs.message.kind` | Requests and one-way records | `COMMAND`, `EVENT` or `QUERY` |
| `cqrs.message.name` | Requests and one-way records | Message name from `MessageNamingStrategy`. Consumers resolve the payload class by this name first (see [Evolving a message](core.md#evolving-a-message)) |
| `cqrs.message.key` | Requests and one-way commands and events whose payload implements `KeyedMessage` with a non-blank key | The declared key |
| `cqrs.payload.type` | Requests, one-way records, replies with a result, error replies | Fully qualified class name of the payload. Consumers fall back to it when the name is unknown locally |
| `cqrs.correlation.id` | Requests and replies | Correlation id of the request |
| `cqrs.reply.topic` | Requests | Reply topic of the sending application |
| `cqrs.request.mode` | Requests | `WAIT` or `REPLY` |
| `cqrs.error` | Error replies | `true` |
| `cqrs.error.type` | Error replies, dead-lettered records | Class name of the handler's exception |
| `cqrs.error.message` | Dead-lettered records | Exception message, truncated to 1000 characters; absent when there is none |
| `cqrs.error.attempts` | Dead-lettered records | Number of deliveries made |
| `cqrs.error.timestamp` | Dead-lettered records | ISO-8601 instant of dead-lettering |
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

These are the only sender-side observations of a Kafka dispatch: the CQRS observability middleware (`TracingMiddleware`, `MicrometerBusObservability`) does not declare `DispatchPhase.OUTBOUND` and runs on the receiver only. See [Distributed Tracing](middleware.md#distributed-tracing).

## Configuration Properties

Defined in `KafkaCqrsProperties` (`cqrs.kafka.*`):

| Property | Type | Default | Description |
|---|---|---|---|
| `cqrs.kafka.enabled` | `boolean` | `true` | Master switch for all Kafka bus adapters. |
| `cqrs.kafka.prefix` | `String` | `"cqrs"` | Prefix for topic names. Blank means no prefix. |
| `cqrs.kafka.auto-create-topics` | `boolean` | `true` | Declares `NewTopic` beans for the enabled buses and the reply topic. |
| `cqrs.kafka.partition-key.strategy` | `MESSAGE_NAME` \| `PAYLOAD_TYPE` \| `NONE` | `MESSAGE_NAME` | Record key used by `DefaultKafkaPartitionKeyStrategy` for messages that declare no `KeyedMessage` key. |
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
| `cqrs.kafka.error-handling.max-attempts` | `int` | `3` | Deliveries of a failed record, the first one included; `1` dead-letters on the first failure. Must be at least 1. |
| `cqrs.kafka.error-handling.back-off.initial-interval` | `Duration` | `1s` | Wait before the first retry. |
| `cqrs.kafka.error-handling.back-off.multiplier` | `double` | `2.0` | Factor applied to the wait after every retry. Must be at least 1. |
| `cqrs.kafka.error-handling.back-off.max-interval` | `Duration` | `10s` | Upper bound of the wait between two deliveries. |
| `cqrs.kafka.error-handling.dead-letter.enabled` | `boolean` | `true` | Publish exhausted and unprocessable records to `{prefix}.{app}.{bus-topic}.dlt`; when `false` they are logged and skipped. |
| `cqrs.kafka.error-handling.dead-letter.partitions` | `int` | `1` | Partitions of the dead-letter topics created with `auto-create-topics`. |
| `cqrs.kafka.error-handling.dead-letter.replicas` | `short` | `1` | Replication factor of the dead-letter topics. |

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
    error-handling:
      max-attempts: 5
      back-off:
        initial-interval: 500ms
        max-interval: 5s
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
| `KafkaCommandBusAutoConfiguration` | `CommandHandlerRegistry` bean + `cqrs.kafka.commands.enabled=true` | `KafkaCommandBus`, `KafkaCommandConsumer`, `cqrsCommandsTopic`, `cqrsCommandsDeadLetterTopic`, `cqrsKafkaCommandListenerContainer` |
| `KafkaEventBusAutoConfiguration` | `EventHandlerRegistry` bean + `cqrs.kafka.events.enabled=true` | `KafkaEventBus`, `KafkaEventConsumer`, `cqrsEventsTopic`, `cqrsEventsDeadLetterTopic`, `cqrsKafkaEventListenerContainer` |
| `KafkaQueryBusAutoConfiguration` | `QueryHandlerRegistry` bean + `cqrs.kafka.queries.enabled=true` | `KafkaQueryBus`, `KafkaQueryConsumer`, `cqrsQueriesTopic`, `cqrsQueriesDeadLetterTopic`, `cqrsKafkaQueryListenerContainer` |

`KafkaTopicNamingStrategy`, `KafkaPartitionKeyStrategy`, `KafkaTemplate<String, byte[]>`, `KafkaMessagePublisher` and `KafkaRequestReplyClient` are `@ConditionalOnMissingBean` and can be replaced with your own bean. The topic beans are only created when `cqrs.kafka.auto-create-topics=true`; the dead-letter topic beans also require `cqrs.kafka.error-handling.dead-letter.enabled=true`. A `CommonErrorHandler` bean replaces the module's error handler on the command, event and query containers (see [Retry and Dead-Letter Topics](#retry-and-dead-letter-topics)).

## Running Next to the RabbitMQ Module

The Kafka and RabbitMQ modules can be on the classpath of the same application. Their beans have distinct names (`cqrsKafka*ListenerContainer` versus the RabbitMQ `cqrs*ListenerContainer`), so both start side by side.

Each transport has its own bus beans: inject `KafkaEventBus` to publish an event over Kafka and `RabbitMqCommandBus` to send a command over RabbitMQ, for example. A message is handled by the consumer of the transport it was sent on.

Both modules start consumers for every bus they enable. To keep a bus off Kafka, set `cqrs.kafka.<bus>.enabled=false`; `cqrs.rabbitmq.enabled=false` turns the whole RabbitMQ module off. See [rabbitmq-adapter.md](rabbitmq-adapter.md) for the RabbitMQ side.
