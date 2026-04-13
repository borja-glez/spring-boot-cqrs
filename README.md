# spring-boot-cqrs

[![Maven Central](https://img.shields.io/maven-central/v/com.borjaglez.cqrs/spring-boot-cqrs-boot3-starter)](https://central.sonatype.com/artifact/com.borjaglez.cqrs/spring-boot-cqrs-boot3-starter)
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
- **Jackson-based serialization** SPI for message transport, including generic `ParameterizedTypeReference` support for distributed request/reply (core is Jackson-free; each starter brings the right version)
- **AggregateRoot** base class with domain event recording
- **Spring Boot 3 and 4 support** via dedicated starter modules
- **100% JaCoCo coverage** (instructions + branches) enforced on every build
- Testcontainers-backed integration coverage and runnable demo applications

## Quick Start

### Spring Boot 3

**Gradle**

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-boot3-starter:0.2.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.cqrs</groupId>
    <artifactId>spring-boot-cqrs-boot3-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Spring Boot 4

**Gradle**

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-boot4-starter:0.2.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.cqrs</groupId>
    <artifactId>spring-boot-cqrs-boot4-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Minimal Example

**1. Define a command:**

```java
@Getter
@CqrsMessage(service = "task-service", module = "task", name = "create-task")
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

That is all you need. The starter auto-configures the bus, discovers your handler at startup, and routes the command.

## Modules

| Module | Description |
|---|---|
| `spring-boot-cqrs-core` | Bus interfaces, base types, annotations, middleware, serialization SPI, AOT support |
| `spring-boot-cqrs-boot3-starter` | Spring Boot 3 auto-configuration |
| `spring-boot-cqrs-boot4-starter` | Spring Boot 4 auto-configuration |
| `spring-boot-cqrs-kafka` | Distributed messaging adapter for Kafka with request/reply support and configurable partition keys |
| `spring-boot-cqrs-rabbitmq` | Distributed messaging adapter with retry and dead-letter queue support |
| `examples` | Runnable sample applications |

Most applications only need the **boot3-starter** or **boot4-starter** (pick the one matching your Spring Boot version). The core module does not depend on Jackson at runtime -- each starter brings the correct Jackson version for its Spring Boot generation. Add the **rabbitmq** or **kafka** module when you need inter-service messaging.

## Usage

### Commands

Commands represent intentions to change state. Each command has exactly one handler.

```java
// Define
@Getter
@CqrsMessage(service = "my-app", module = "order", name = "create-order")
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
@CqrsMessage(service = "my-app", module = "order", name = "order-created")
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

This improves transactional consistency, but it is **not** a durable delivery mechanism for external brokers. If you need reliable broker publication, use the **Outbox Pattern**. See **[examples/example-outbox](examples/example-outbox)** for a complete optional example.

### Queries

Queries represent read requests. Each query has exactly one handler.

```java
// Define
@CqrsMessage(service = "my-app", module = "order", name = "get-order")
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

| Middleware | Enabled by default | Property |
|---|---|---|
| `CommandValidationInterceptor` | Yes (when JSR-380 is on classpath) | `cqrs.validation.enabled` |
| `MicrometerBusObservability` | Yes (when Micrometer is on classpath) | `cqrs.observability.enabled` |

### RabbitMQ

Add the RabbitMQ module to distribute commands, events, and queries across services (alongside your chosen starter):

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-rabbitmq:0.2.0")
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

The RabbitMQ event bus automatically falls back to the local Spring event bus if the AMQP connection is unavailable.

### Kafka

Add the Kafka module to distribute commands, events, and queries across services (alongside your chosen starter):

```kotlin
implementation("com.borjaglez.cqrs:spring-boot-cqrs-kafka:0.2.0")
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

Kafka request/reply supports `ParameterizedTypeReference`, so generic responses such as `List<OrderDto>` or nested wrapper types are preserved during deserialization.

Partition keys are configurable through `cqrs.kafka.partition-key.strategy`:

- `MESSAGE_NAME` -- route by CQRS message name (default)
- `PAYLOAD_TYPE` -- route by Java payload type
- `NONE` -- send records without a key

### Configuration Reference

| Property | Default | Description |
|---|---|---|
| `cqrs.naming.prefix` | `""` | Prefix for generated message names |
| `cqrs.events.transactional` | `true` | Publish events after transaction commit when a Spring transaction is active |
| `cqrs.validation.enabled` | `true` | Enable JSR-380 command validation middleware |
| `cqrs.observability.enabled` | `true` | Enable Micrometer observability middleware |
| `cqrs.kafka.enabled` | `true` | Enable Kafka bus adapters |
| `cqrs.kafka.prefix` | `"cqrs"` | Prefix for Kafka topic names |
| `cqrs.kafka.auto-create-topics` | `true` | Auto-register CQRS topics through Spring Kafka |
| `cqrs.kafka.partition-key.strategy` | `MESSAGE_NAME` | Strategy used to compute Kafka record keys |
| `cqrs.kafka.replies.topic` | `"replies"` | Base reply topic name used for request/reply |
| `cqrs.kafka.replies.partitions` | `1` | Reply topic partition count |
| `cqrs.kafka.replies.replicas` | `1` | Reply topic replication factor |
| `cqrs.kafka.replies.timeout` | `30s` | Timeout for distributed command/query replies |
| `cqrs.kafka.commands.topic` | `"commands"` | Command topic name |
| `cqrs.kafka.commands.partitions` | `3` | Command topic partition count |
| `cqrs.kafka.commands.replicas` | `1` | Command topic replication factor |
| `cqrs.kafka.commands.concurrency` | `1` | Command listener concurrency |
| `cqrs.kafka.events.topic` | `"events"` | Event topic name |
| `cqrs.kafka.events.partitions` | `3` | Event topic partition count |
| `cqrs.kafka.events.replicas` | `1` | Event topic replication factor |
| `cqrs.kafka.events.concurrency` | `1` | Event listener concurrency |
| `cqrs.kafka.queries.topic` | `"queries"` | Query topic name |
| `cqrs.kafka.queries.partitions` | `3` | Query topic partition count |
| `cqrs.kafka.queries.replicas` | `1` | Query topic replication factor |
| `cqrs.kafka.queries.concurrency` | `1` | Query listener concurrency |
| `cqrs.rabbitmq.enabled` | `true` | Enable RabbitMQ bus adapters |
| `cqrs.rabbitmq.prefix` | `"cqrs"` | Prefix for RabbitMQ exchange and queue names |
| `cqrs.rabbitmq.retry.max-attempts` | `3` | Max retry attempts before dead-lettering |
| `cqrs.rabbitmq.retry.ttl` | `1000` | Retry queue TTL in milliseconds |
| `cqrs.rabbitmq.commands.exchange` | `"commands"` | Command exchange name |
| `cqrs.rabbitmq.commands.concurrent-consumers` | `10` | Min concurrent command consumers |
| `cqrs.rabbitmq.commands.max-concurrent-consumers` | `20` | Max concurrent command consumers |
| `cqrs.rabbitmq.events.exchange` | `"events"` | Event exchange name |
| `cqrs.rabbitmq.events.concurrent-consumers` | `10` | Min concurrent event consumers |
| `cqrs.rabbitmq.events.max-concurrent-consumers` | `20` | Max concurrent event consumers |
| `cqrs.rabbitmq.queries.exchange` | `"queries"` | Query exchange name |
| `cqrs.rabbitmq.queries.concurrent-consumers` | `10` | Min concurrent query consumers |
| `cqrs.rabbitmq.queries.max-concurrent-consumers` | `20` | Max concurrent query consumers |

See [docs/configuration.md](docs/configuration.md) for full details with YAML examples.

## Examples

The repository includes five example applications:

- **[example-basic](examples/example-basic)** -- Commands, events, queries, and a REST controller (Spring Boot 3)
- **[example-outbox](examples/example-outbox)** -- Optional Outbox Pattern with JPA, H2, and scheduled publication (Spring Boot 3)
- **[example-middleware](examples/example-middleware)** -- Custom logging, authorization, and transactional middleware (Spring Boot 3)
- **[example-rabbitmq](examples/example-rabbitmq)** -- Distributed messaging with RabbitMQ (Spring Boot 3)
- **[boot4-demo](examples/boot4-demo)** -- Minimal example running on Spring Boot 4

## Documentation

- [Core Module](docs/core.md) -- Base types, annotations, registries, bus interfaces, serialization
- [Middleware](docs/middleware.md) -- Pipeline, custom middleware, built-in interceptors
- [Kafka Adapter](docs/kafka-adapter.md) -- Topics, request/reply, partition keys, consumers
- [RabbitMQ Adapter](docs/rabbitmq-adapter.md) -- Exchanges, queues, retry, dead-letter, consumers
- [Configuration Reference](docs/configuration.md) -- All properties with YAML examples
- [GraalVM Native](docs/graalvm-native.md) -- AOT support, native image builds
- [Migration Guide](docs/migration-guide.md) -- Migrating from the original amj-bus implementation

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

Apply code formatting before committing:

```bash
./gradlew :spring-boot-cqrs-core:spotlessApply
./gradlew :spring-boot-cqrs-rabbitmq:spotlessApply
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

See [LICENSE](LICENSE).
