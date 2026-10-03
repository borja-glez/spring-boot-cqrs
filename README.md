# spring-boot-cqrs

[![Maven Central](https://img.shields.io/maven-central/v/com.borjaglez.cqrs/spring-boot-cqrs-core)](https://central.sonatype.com/artifact/com.borjaglez.cqrs/spring-boot-cqrs-boot3-starter)
[![CI](https://github.com/borja-glez/spring-boot-cqrs/actions/workflows/ci.yml/badge.svg)](https://github.com/borja-glez/spring-boot-cqrs/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/borja-glez/spring-boot-cqrs)](LICENSE)
![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)

Production-grade, GraalVM-compatible CQRS library for Spring Boot 3 and Spring Boot 4.

## Features

- **Command, Event, and Query buses** with in-process Spring implementations
- **Middleware pipeline** for cross-cutting concerns (logging, validation, auth, transactions)
- **RabbitMQ and Kafka distributed messaging** as drop-in adapters for all three buses
- **GraalVM native image support** with automatic AOT hint registration
- **Annotation-driven handler discovery** -- no manual wiring required
- **Spring Boot auto-configuration** -- add the dependency and start coding
- **Micrometer observability** middleware with per-message-type metrics
- **JSR-380 Bean Validation** middleware for command validation
- **Message context propagation** with auto-generated correlation IDs, SLF4J MDC mirroring, and transport header propagation across RabbitMQ/Kafka
- **Actuator endpoints** (`/actuator/cqrs` + `info` contributor) exposing handlers, middlewares and message types when `spring-boot-starter-actuator` is present
- **Test support module** with spy buses, in-memory buses, AssertJ assertions, and a `@CqrsTest` slice annotation
- **Jackson-based serialization** SPI for message transport, including generic `ParameterizedTypeReference` support for distributed request/reply (core is Jackson-free; each starter brings the right version)
- **AggregateRoot** base class with domain event recording
- **Spring Boot 3 and 4 support** via dedicated starter modules
- **100% JaCoCo coverage** (instructions + branches) enforced on every build
- Testcontainers-backed integration coverage and runnable demo applications

## Quick Start

### Requirements

- Java 21+ (tested on 21 and 25)
- Spring Boot 3.5.x or 4.0.x

### Spring Boot 3

**Gradle**

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-boot3-starter:0.5.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.cqrs</groupId>
    <artifactId>spring-boot-cqrs-boot3-starter</artifactId>
    <version>0.5.0</version>
</dependency>
```

### Spring Boot 4

**Gradle**

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-boot4-starter:0.5.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.cqrs</groupId>
    <artifactId>spring-boot-cqrs-boot4-starter</artifactId>
    <version>0.5.0</version>
</dependency>
```

### Upgrading

Upgrading from an earlier version? [docs/upgrading.md](docs/upgrading.md) lists the breaking
changes of each release with before/after code and migration steps, and
[CHANGELOG.md](CHANGELOG.md) marks them with **BREAKING:**.

### Minimal Example

**1. Define a command:**

```java
@Getter
public class CreateTaskCommand extends Command {
    private final String title;

    public CreateTaskCommand(String title) {
        super();
        this.title = title;
    }
}
```

**2. Create a handler:**

```java
@CommandHandler
public class CreateTaskCommandHandler {

    @HandleCommand
    public String handle(CreateTaskCommand command) {
        // your business logic here
        return "task-123";
    }
}
```

**3. Dispatch from a controller:**

```java
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final CommandBus commandBus;

    public TaskController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody CreateTaskRequest request) {
        String taskId = commandBus.dispatchAndReceive(
            new CreateTaskCommand(request.title()));
        return ResponseEntity.status(HttpStatus.CREATED).body(taskId);
    }
}
```

That is all you need. The starter auto-configures the bus, discovers your handler at startup, and routes the command. The in-process buses route by Java type, so messages need no annotation; annotate with `@CqrsMessage` the messages that other services send or receive (see [Message names and `@CqrsMessage`](#message-names-and-cqrsmessage)).

## Modules

| Module | Description |
|---|---|
| `spring-boot-cqrs-core` | Bus interfaces, base types, annotations, middleware, serialization SPI, AOT support |
| `spring-boot-cqrs-boot3-starter` | Spring Boot 3 auto-configuration |
| `spring-boot-cqrs-boot4-starter` | Spring Boot 4 auto-configuration |
| `spring-boot-cqrs-kafka` | Distributed messaging adapter for Kafka with request/reply support and configurable partition keys |
| `spring-boot-cqrs-rabbitmq` | Distributed messaging adapter with retry and dead-letter queue support |
| `spring-boot-cqrs-jdbc` | JDBC idempotency store for `@Idempotent` handlers and transactional outbox for reliable event publication |
| `spring-boot-cqrs-test` | Test utilities: spy/in-memory buses, AssertJ assertions, `@CqrsTest` slice annotation |
| `examples` | Runnable sample applications |

Most applications only need the **boot3-starter** or **boot4-starter** (pick the one matching your Spring Boot version). The core module does not depend on Jackson at runtime -- each starter brings the correct Jackson version for its Spring Boot generation. Add the **rabbitmq** or **kafka** module when you need inter-service messaging.

## Usage

### Commands

Commands represent intentions to change state. Each command has exactly one handler.

```java
// Define
@Getter
public class CreateOrderCommand extends Command {
    private final String product;
    public CreateOrderCommand(String product) {
        super();
        this.product = product;
    }
}

// Handle
@CommandHandler
public class CreateOrderCommandHandler {
    @HandleCommand
    public String handle(CreateOrderCommand command) {
        return "order-id-123";
    }
}

// Dispatch
commandBus.dispatch(command);             // fire-and-forget
String id = commandBus.dispatchAndReceive(command);  // with return value
```

### Events

Events represent facts that have occurred. An event can have zero or many handlers.

```java
// Define
@Getter
public class OrderCreatedEvent extends Event {
    private final String orderId;
    public OrderCreatedEvent(String orderId) {
        super();
        this.orderId = orderId;
    }
}

// Handle
@EventHandler
public class OrderEventHandler {
    @HandleEvent
    public void onOrderCreated(OrderCreatedEvent event) {
        // react to the event
    }
}

// Publish
eventBus.publish(new OrderCreatedEvent("order-123"));
eventBus.publish(List.of(event1, event2));
```

Use `AggregateRoot` to collect domain events and publish them after state changes:

```java
public class Order extends AggregateRoot {
    public void confirm() {
        // change state...
        record(new OrderConfirmedEvent(this.id));
    }
}

// In a handler:
List<Event> events = order.pullEvents();
eventBus.publish(events);
```

#### Transactional event publishing

When a Spring transaction is active, the default `EventBus` created by the Boot starters queues events and publishes them **after commit**.

- if the transaction commits, queued events are published
- if the transaction rolls back, queued events are discarded
- if no transaction is active, events are published immediately

You can disable this behavior with:

```yaml
cqrs:
  events:
    transactional: false
```

This improves transactional consistency, but it is **not** a durable delivery mechanism for external brokers: the event is lost if the process stops or the broker is down after commit. To publish to Kafka or RabbitMQ reliably, enable the transactional outbox of `spring-boot-cqrs-jdbc` and publish through `OutboxEventBus`: the event is stored in your transaction and relayed after commit, at least once. See [docs/outbox.md](docs/outbox.md) and [examples/example-outbox](examples/example-outbox).

### Queries

Queries represent read requests. Each query has exactly one handler.

```java
// Define
public class GetOrderQuery extends Query {
    @Getter private final String orderId;
    public GetOrderQuery(String orderId) {
        super();
        this.orderId = orderId;
    }
}

// Handle
@QueryHandler
public class OrderQueryHandler {
    @HandleQuery
    public Order getOrder(GetOrderQuery query) {
        return repository.findById(query.getOrderId()).orElseThrow();
    }
}

// Ask
Order order = queryBus.ask(new GetOrderQuery("order-123"));
```

### Message names and `@CqrsMessage`

`@CqrsMessage` gives a message a stable, service-qualified name. It is optional: the in-process buses dispatch by Java type and never read it, so commands, events and queries handled inside one application need no annotation.

Annotate the messages that cross a service boundary, the public contract of a service:

- a command or query that another service sends to this one;
- an event that other services consume.

```java
@Getter
@CqrsMessage(service = "orders", module = "order", name = "order-placed")
public class OrderPlaced extends Event {
    private final String orderId;
    public OrderPlaced(String orderId) {
        super();
        this.orderId = orderId;
    }
}
```

`MessageNamingStrategy` turns it into `{prefix}.{service}.{version}.{type}.{module}.{name}`, for example `orders.1.event.order.order-placed`. A message without the annotation is named after its class in kebab-case (`order-placed`), a name that can collide between services and changes when the class is renamed.

What the name is used for:

| Transport | Use of the message name | Without `@CqrsMessage` |
|---|---|---|
| In-process buses | None (dispatch by Java type) | Works the same |
| RabbitMQ | Routing key; only annotated messages are exposed by default (`cqrs.rabbitmq.expose=annotated`) | The message stays local: it is not bound and is rejected if it reaches the queue |
| Kafka | `cqrs.message.name` header and default partition key (`MESSAGE_NAME`) | Still delivered (consumers match the payload type), with the kebab-case class name as header and key |

Keep contract messages in a module shared by the services that exchange them, and keep in-process messages next to their handlers. See [docs/core.md](docs/core.md#cqrsmessage) for the attributes.

### Middleware

Middleware intercepts all bus dispatches. Implement `BusMiddleware` and register as a Spring bean. Use `@Order` to control execution order.

```java
@Component
@Order(1)
public class LoggingMiddleware implements BusMiddleware {

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
        log.info("Processing: {}", message.getClass().getSimpleName());
        long start = System.currentTimeMillis();
        try {
            Object result = chain.proceed(message);
            log.info("Completed in {}ms", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("Failed: {}", message.getClass().getSimpleName(), e);
            throw e;
        }
    }
}
```

**Built-in middleware:**

| Middleware | Enabled by default | Property | Runs on the sender of remote buses |
|---|---|---|---|
| `CommandValidationInterceptor` | Yes (when JSR-380 is on classpath) | `cqrs.validation.enabled` | Yes |
| `MicrometerBusObservability` | Yes (when Micrometer is on classpath) | `cqrs.observability.enabled` | No |
| `ContextPropagationMiddleware` | Yes (when SLF4J is on classpath) | `cqrs.context.enabled` | Yes |
| `TracingMiddleware` | Yes (when an `ObservationRegistry` bean is present) | `cqrs.tracing.enabled` | No |
| `RetryMiddleware` | No (opt-in; commands and queries only) | `cqrs.retry.enabled` | No |

**Where middleware runs.** `BusMiddleware.phases()` returns the `DispatchPhase`s of a middleware: `LOCAL` (in-process buses), `OUTBOUND` (the sending side of the RabbitMQ and Kafka buses, before the message is published) and `INBOUND` (their consumers, before the handler). It defaults to `LOCAL` and `INBOUND`; override it to run a middleware before remote sends too. Since 0.4.0 an invalid command sent through a remote bus fails in the caller with `ConstraintViolationException`, and remote messages sent without a context carry a correlation id generated by the sender. See [Middleware on Remote Buses](docs/middleware.md#middleware-on-remote-buses).

### Idempotent handlers

RabbitMQ and Kafka deliver at least once. Annotate a command or event handler with `@Idempotent`
to run it at most once per message; add `spring-boot-cqrs-jdbc` so the marker commits in the same
transaction as the handler's work. See [docs/idempotency.md](docs/idempotency.md).

### Message Context & Correlation ID

`MessageContext` is an immutable map of business metadata (`correlationId`, `tenantId`, `userId`, …) that flows through every bus dispatch via a `ThreadLocal`. The auto-configured `ContextPropagationMiddleware` runs with highest precedence on every bus and:

- Generates a `correlationId` (UUID) when none is present.
- Mirrors configured keys into SLF4J MDC so downstream logs carry them automatically.
- Serializes the context into RabbitMQ and Kafka headers on publish, and rehydrates it on the consumer side — no application code needed to propagate context across services.

Read the context inside any handler or middleware:

```java
@CommandHandler
public class CreateOrderHandler {

  @HandleCommand
  public OrderId handle(CreateOrderCommand command) {
    String correlationId = MessageContext.current().correlationId();
    String tenantId = MessageContext.current().get("tenantId").orElse("-");
    // ...
  }
}
```

Seed the context at a system boundary (web filter, scheduler, inbound adapter):

```java
MessageContext ctx =
    MessageContext.empty()
        .with(MessageContext.CORRELATION_ID_KEY, request.getHeader("X-Correlation-Id"))
        .with("tenantId", resolvedTenant);

try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
  commandBus.dispatch(command);
}
```

The context does not follow work to another thread by itself. Carry it into executors and `@Async` methods with `MessageContext.wrap(...)` or `MessageContextTaskDecorator`, or, with `io.micrometer:context-propagation` on the classpath, with Spring's `ContextPropagatingTaskDecorator` (the starters register a `ThreadLocalAccessor` for `MessageContext`):

```java
executor.submit(MessageContext.wrap(() -> commandBus.dispatch(new ReserveStock(orderId))));
```

Work picked up later by a poller or a scheduled job has no caller thread to copy from: store the `correlationId` with the work item and reopen a `MessageContext.scope` when it runs.

See [docs/middleware.md](docs/middleware.md#message-context--correlation-id) for the full API and [docs/configuration.md](docs/configuration.md#context-propagation-properties) for tuning.

### RabbitMQ

Add the RabbitMQ module to distribute commands, events, and queries across services (alongside your chosen starter):

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-rabbitmq:0.5.0")
```

Configure in `application.yml`:

```yaml
spring:
  application:
    name: my-service
  rabbitmq:
    host: localhost
    port: 5672

cqrs:
  naming:
    prefix: my-service
  rabbitmq:
    enabled: true
    prefix: my-service
    commands:
      exchange: commands
      concurrent-consumers: 5
    events:
      exchange: events
      concurrent-consumers: 5
    queries:
      exchange: queries
      concurrent-consumers: 5
```

RabbitMQ starts automatically via [Spring Boot Docker Compose](https://docs.spring.io/spring-boot/reference/features/docker-compose.html) when running `./gradlew :examples:example-rabbitmq:bootRun` — no manual `docker compose up` is needed. Connection properties are auto-configured from the running container.

The RabbitMQ event bus throws when the event cannot be sent; it does not fall back to the local event bus. `publish(List<Event>)` stops at the first failure. By default the bus does not wait for the broker to accept the event; set `cqrs.rabbitmq.events.confirms.enabled=true` (with `spring.rabbitmq.publisher-confirm-type=correlated`) to fail when the broker rejects it or does not confirm it within `cqrs.rabbitmq.events.confirms.timeout` (5s). For reliable publication, use an outbox (see [Transactional event publishing](#transactional-event-publishing)).

Only messages annotated with `@CqrsMessage` are exposed over RabbitMQ by default (`cqrs.rabbitmq.expose=annotated`): other handled messages get no binding and are rejected if they reach the queue anyway, so in-process commands cannot be triggered by other producers on the broker. Mark a handler `remote = false` (`@HandleCommand(remote = false)`, or on the handler class) to keep an annotated message local too, or set `cqrs.rabbitmq.expose=all` to expose every handled message as before 0.4.0. See [Exposed and local messages](docs/rabbitmq-adapter.md#exposed-and-local-messages).

### Kafka

Add the Kafka module to distribute commands, events, and queries across services (alongside your chosen starter):

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-kafka:0.5.0")
```

Configure in `application.yml`:

```yaml
spring:
  application:
    name: my-service
  kafka:
    bootstrap-servers: localhost:9092

cqrs:
  naming:
    prefix: my-service
  kafka:
    enabled: true
    prefix: my-service
    auto-create-topics: true
    partition-key:
      strategy: MESSAGE_NAME
    replies:
      topic: replies
      timeout: 30s
    commands:
      topic: commands
      partitions: 3
      replicas: 1
      concurrency: 1
    events:
      topic: events
      partitions: 3
      replicas: 1
      concurrency: 1
    queries:
      topic: queries
      partitions: 3
      replicas: 1
      concurrency: 1
```

The Kafka event bus throws when the broker does not acknowledge an event; it does not fall back to the local event bus. `publish(List<Event>)` stops at the first failure. For reliable publication, use an outbox (see [Transactional event publishing](#transactional-event-publishing)).

Generic results such as `List<OrderDto>` need a `ParameterizedTypeReference`; see [Generic results over RabbitMQ and Kafka](#generic-results-over-rabbitmq-and-kafka).

Replies are read from the per-application reply topic (`<prefix>.<spring.application.name>.replies`). Each instance consumes it with its own consumer group (`<appName>.cqrs.replies.<random>`), so every instance sees every reply and keeps the ones it is waiting for. On startup the reply consumer starts at the instance start time instead of the beginning of the topic, so replies from previous runs are not read again; `spring.kafka.consumer.auto-offset-reset` keeps applying to the command, event and query consumers. Groups left by previous starts have no members and expire in the broker after `offsets.retention.minutes` (seven days by default). No configuration is needed.

Partition keys are configurable through `cqrs.kafka.partition-key.strategy`:

- `MESSAGE_NAME` -- route by CQRS message name (default)
- `PAYLOAD_TYPE` -- route by Java payload type
- `NONE` -- send records without a key

Kafka orders records per partition, so with these strategies messages keep their order only within one message type. To keep all the messages of one aggregate in order (an `OrderPlaced` before its `OrderCancelled`), let commands and events implement `KeyedMessage`: its key becomes the record key (and the `cqrs.message.key` header), and a `null` or blank key falls back to the configured strategy.

```java
public class OrderCancelled extends Event implements KeyedMessage {
  private UUID orderId;

  @Override
  public String messageKey() {
    return orderId.toString();
  }
}
```

See [Partition Keys](docs/kafka-adapter.md#partition-keys) for details.

### Generic results over RabbitMQ and Kafka

A remote reply carries only the runtime class of the handler's result, and the requester rebuilds the result from that erased class. Results whose class describes them fully (records, POJOs, `String`, boxed primitives) come back as they were sent. A generic result (a collection, a map, a generic wrapper such as `Page<OrderDto>`) comes back with untyped content, `LinkedHashMap` instead of `OrderDto`, unless the caller passes a `ParameterizedTypeReference`:

```java
@QueryHandler
class OrderQueries {
    @HandleQuery
    List<OrderDto> on(ListOrders query) {
        return orders.stream().map(OrderDto::from).toList();
    }
}

// Requester, over RabbitMQ or Kafka
List<OrderDto> orders =
    queryBus.ask(new ListOrders(), new ParameterizedTypeReference<List<OrderDto>>() {});

Result<OrderDto> placed =
    commandBus.dispatchAndReceive(
        new PlaceOrder("o-1"), new ParameterizedTypeReference<Result<OrderDto>>() {});

// Without the type reference the elements are maps: untyped.get(0) is a LinkedHashMap,
// and using it as an OrderDto fails with a ClassCastException.
List<OrderDto> untyped = queryBus.ask(new ListOrders());
```

The overloads are `QueryBus.ask(Query, ParameterizedTypeReference<R>)` and `CommandBus.dispatchAndReceive(Command, ParameterizedTypeReference<R>)`. The local buses return the handler's object as it is and do not need them, so passing the type reference keeps the same call working when a bus moves from local to remote.

### Configuration Reference

| Property | Default | Description |
|---|---|---|
| `cqrs.naming.prefix` | `""` | Prefix for generated message names |
| `cqrs.events.transactional` | `true` | Publish events after transaction commit when a Spring transaction is active |
| `cqrs.validation.enabled` | `true` | Enable JSR-380 command validation middleware |
| `cqrs.observability.enabled` | `true` | Enable Micrometer observability middleware |
| `cqrs.context.enabled` | `true` | Enable `MessageContext` propagation + SLF4J MDC middleware |
| `cqrs.context.auto-correlation-id` | `true` | Generate a UUID `correlationId` when none is present on dispatch entry |
| `cqrs.context.mdc-keys` | `[correlationId]` | Context keys mirrored into SLF4J MDC during handler execution |
| `cqrs.context.header-prefix` | `"cqrs.context."` | Prefix applied to RabbitMQ/Kafka headers when serializing context across services |
| `cqrs.retry.enabled` | `false` | Enable the in-process retry middleware for commands and queries |
| `cqrs.retry.max-attempts` | `3` | Total attempts, the first one included |
| `cqrs.retry.backoff.strategy` | `exponential-jitter` | `fixed`, `exponential` or `exponential-jitter` |
| `cqrs.retry.backoff.initial-delay` | `100ms` | Delay after the first failed attempt |
| `cqrs.retry.backoff.multiplier` | `2.0` | Delay growth factor per failed attempt |
| `cqrs.retry.backoff.max-delay` | `5s` | Upper bound of the delay |
| `cqrs.retry.backoff.jitter-factor` | `0.1` | Random spread of the delay, in `[0, 1]` |
| `cqrs.retry.retriable-exceptions` | `[]` | Exception classes retried; empty means `RuntimeException` |
| `cqrs.retry.non-retriable-exceptions` | `[]` | Exception classes added to the default non-retriable set |
| `cqrs.kafka.enabled` | `true` | Enable Kafka bus adapters |
| `cqrs.kafka.prefix` | `"cqrs"` | Prefix for Kafka topic names |
| `cqrs.kafka.auto-create-topics` | `true` | Auto-register CQRS topics through Spring Kafka |
| `cqrs.kafka.partition-key.strategy` | `MESSAGE_NAME` | Strategy used to compute Kafka record keys of messages that declare no `KeyedMessage` key |
| `cqrs.kafka.replies.topic` | `"replies"` | Base reply topic name used for request/reply |
| `cqrs.kafka.replies.partitions` | `1` | Reply topic partition count |
| `cqrs.kafka.replies.replicas` | `1` | Reply topic replication factor |
| `cqrs.kafka.replies.timeout` | `30s` | Timeout for distributed command/query replies |
| `cqrs.kafka.commands.enabled` | `true` | Whether commands use Kafka |
| `cqrs.kafka.commands.topic` | `"commands"` | Command topic name |
| `cqrs.kafka.commands.partitions` | `3` | Command topic partition count |
| `cqrs.kafka.commands.replicas` | `1` | Command topic replication factor |
| `cqrs.kafka.commands.concurrency` | `1` | Command listener concurrency |
| `cqrs.kafka.commands.group-id` | `""` | Command consumer group; blank means `{spring.application.name}.cqrs.commands` |
| `cqrs.kafka.events.enabled` | `true` | Whether events use Kafka |
| `cqrs.kafka.events.topic` | `"events"` | Event topic name |
| `cqrs.kafka.events.partitions` | `3` | Event topic partition count |
| `cqrs.kafka.events.replicas` | `1` | Event topic replication factor |
| `cqrs.kafka.events.concurrency` | `1` | Event listener concurrency |
| `cqrs.kafka.events.group-id` | `""` | Event consumer group; blank means `{spring.application.name}.cqrs.events` |
| `cqrs.kafka.queries.enabled` | `true` | Whether queries use Kafka |
| `cqrs.kafka.queries.topic` | `"queries"` | Query topic name |
| `cqrs.kafka.queries.partitions` | `3` | Query topic partition count |
| `cqrs.kafka.queries.replicas` | `1` | Query topic replication factor |
| `cqrs.kafka.queries.concurrency` | `1` | Query listener concurrency |
| `cqrs.kafka.queries.group-id` | `""` | Query consumer group; blank means `{spring.application.name}.cqrs.queries` |
| `cqrs.kafka.error-handling.max-attempts` | `3` | Deliveries of a failed command, event or query record, the first one included |
| `cqrs.kafka.error-handling.back-off.initial-interval` | `1s` | Wait before the first retry |
| `cqrs.kafka.error-handling.back-off.multiplier` | `2.0` | Factor applied to the wait after every retry |
| `cqrs.kafka.error-handling.back-off.max-interval` | `10s` | Upper bound of the wait between two deliveries |
| `cqrs.kafka.error-handling.dead-letter.enabled` | `true` | Publish exhausted records to the per-application dead-letter topic `{prefix}.{spring.application.name}.{topic}.dlt` |
| `cqrs.kafka.error-handling.dead-letter.partitions` | `1` | Dead-letter topic partition count |
| `cqrs.kafka.error-handling.dead-letter.replicas` | `1` | Dead-letter topic replication factor |
| `cqrs.rabbitmq.enabled` | `true` | Enable RabbitMQ bus adapters |
| `cqrs.rabbitmq.prefix` | `"cqrs"` | Prefix for RabbitMQ exchange and queue names |
| `cqrs.rabbitmq.expose` | `annotated` | Messages exposed over RabbitMQ: `annotated` (only `@CqrsMessage` messages) or `all` |
| `cqrs.rabbitmq.retry.max-attempts` | `3` | Max retry attempts before dead-lettering |
| `cqrs.rabbitmq.retry.ttl` | `1000` | Retry queue TTL in milliseconds |
| `cqrs.rabbitmq.commands.exchange` | `"commands"` | Command exchange name |
| `cqrs.rabbitmq.commands.concurrent-consumers` | `10` | Min concurrent command consumers |
| `cqrs.rabbitmq.commands.max-concurrent-consumers` | `20` | Max concurrent command consumers |
| `cqrs.rabbitmq.commands.reply-timeout` | unset | How long `dispatchAndReceive` waits for the reply; unset uses `spring.rabbitmq.template.reply-timeout` |
| `cqrs.rabbitmq.events.exchange` | `"events"` | Event exchange name |
| `cqrs.rabbitmq.events.concurrent-consumers` | `10` | Min concurrent event consumers |
| `cqrs.rabbitmq.events.max-concurrent-consumers` | `20` | Max concurrent event consumers |
| `cqrs.rabbitmq.queries.exchange` | `"queries"` | Query exchange name |
| `cqrs.rabbitmq.queries.concurrent-consumers` | `10` | Min concurrent query consumers |
| `cqrs.rabbitmq.queries.max-concurrent-consumers` | `20` | Max concurrent query consumers |
| `cqrs.rabbitmq.queries.reply-timeout` | unset | How long `ask` waits for the reply; unset uses `spring.rabbitmq.template.reply-timeout` ([details](docs/rabbitmq-adapter.md#reply-timeout-per-bus)) |

See [docs/configuration.md](docs/configuration.md) for full details with YAML examples.

## Examples

The repository includes five example applications:

- **[example-basic](examples/example-basic)** -- Commands, events, queries, and a REST controller (Spring Boot 3)
- **[example-outbox](examples/example-outbox)** -- Transactional outbox with PostgreSQL and Kafka (Spring Boot 3)
- **[example-middleware](examples/example-middleware)** -- Custom logging, authorization, and transactional middleware (Spring Boot 3)
- **[example-rabbitmq](examples/example-rabbitmq)** -- Distributed messaging with RabbitMQ (Spring Boot 3)
- **[boot4-demo](examples/boot4-demo)** -- Minimal example running on Spring Boot 4

## Documentation

- [Core Module](docs/core.md) -- Base types, annotations, registries, bus interfaces, serialization
- [Middleware](docs/middleware.md) -- Pipeline, custom middleware, built-in interceptors
- [Kafka Adapter](docs/kafka-adapter.md) -- Topics, request/reply, partition keys, consumers
- [RabbitMQ Adapter](docs/rabbitmq-adapter.md) -- Exchanges, queues, retry, dead-letter, consumers
- [Idempotent Handlers](docs/idempotency.md) -- @Idempotent, stores, schema, retention
- [Transactional Outbox](docs/outbox.md) -- OutboxEventBus, relay, guarantees, schema
- [Configuration Reference](docs/configuration.md) -- All properties with YAML examples
- [Actuator Endpoints](docs/actuator.md) -- `/actuator/cqrs`, `info` contributor, transport health
- [Testing](docs/testing.md) -- Test utilities: spy buses, in-memory buses, AssertJ assertions, `@CqrsTest` slice
- [GraalVM Native](docs/graalvm-native.md) -- AOT support, native image builds
- [Upgrading](docs/upgrading.md) -- Breaking changes of each release and how to migrate

## GraalVM Native

The library supports GraalVM native images out of the box with both Spring Boot 3 and Spring Boot 4. All CQRS annotations and handler classes are automatically registered for reflection via `CqrsRuntimeHintsRegistrar` and `CqrsBeanRegistrationAotProcessor`. No additional configuration is needed.

See [docs/graalvm-native.md](docs/graalvm-native.md) for details.

## Building

Run all tests and coverage verification:

```bash
./gradlew quality
```

Build a single module:

```bash
./gradlew :spring-boot-cqrs-core:build
```

Run a single test class:

```bash
./gradlew :spring-boot-cqrs-core:test --tests "com.borjaglez.cqrs.command.CommandTest"
```

The library is compiled for Java 21. To run the tests on a newer JDK (compilation stays on
Java 21), pass `testJavaVersion`; CI runs every suite on JDK 21 and 25:

```bash
./gradlew quality -PtestJavaVersion=25
```

Apply code formatting before committing:

```bash
./gradlew :spring-boot-cqrs-core:spotlessApply
./gradlew :spring-boot-cqrs-rabbitmq:spotlessApply
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

See [LICENSE](LICENSE).
