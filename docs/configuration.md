# Configuration Reference

All configuration properties use the `cqrs.*` prefix and are managed through Spring Boot's `@ConfigurationProperties` mechanism. The properties are identical for both Spring Boot 3 (boot3-starter) and Spring Boot 4 (boot4-starter).

## Table of Contents

- [Core Properties](#core-properties)
- [RabbitMQ Properties](#rabbitmq-properties)
- [Full YAML Example](#full-yaml-example)
- [Minimal YAML Example](#minimal-yaml-example)

## Core Properties

Defined in `CqrsProperties` (`cqrs.*`):

| Property | Type | Default | Description |
|---|---|---|---|
| `cqrs.naming.prefix` | `String` | `""` (empty) | Prefix prepended to all generated message names. Used in `@CqrsMessage` name resolution. |
| `cqrs.validation.enabled` | `boolean` | `true` | Enables the `CommandValidationInterceptor` middleware. Requires `jakarta.validation` on the classpath. |
| `cqrs.observability.enabled` | `boolean` | `true` | Enables the `MicrometerBusObservability` middleware. Requires Micrometer on the classpath. |

### Naming Prefix

The naming prefix is used by `DefaultMessageNamingStrategy` when resolving names from `@CqrsMessage`:

```yaml
cqrs:
  naming:
    prefix: my-service
```

With this configuration and a command annotated as:

```java
@CqrsMessage(service = "orders", version = 1, module = "order", name = "create")
public class CreateOrderCommand extends Command { ... }
```

The generated command name is: `my-service.orders.1.command.order.create`

Without a prefix, it becomes: `orders.1.command.order.create`

### Validation

```yaml
cqrs:
  validation:
    enabled: true  # default
```

When enabled, and a JSR-380 `Validator` bean is present, the `CommandValidationInterceptor` is registered as middleware. It validates all `Command` instances before they reach their handler.

To disable:

```yaml
cqrs:
  validation:
    enabled: false
```

### Observability

```yaml
cqrs:
  observability:
    enabled: true  # default
```

When enabled, and a Micrometer `MeterRegistry` bean is present, the `MicrometerBusObservability` is registered as middleware. It records `cqrs.bus.dispatch` timers for every dispatched message.

## RabbitMQ Properties

Defined in `RabbitMqCqrsProperties` (`cqrs.rabbitmq.*`):

| Property | Type | Default | Description |
|---|---|---|---|
| `cqrs.rabbitmq.enabled` | `boolean` | `true` | Master switch for all RabbitMQ bus adapters. Set to `false` to use only in-process buses. |
| `cqrs.rabbitmq.prefix` | `String` | `"cqrs"` | Prefix for RabbitMQ exchange and queue names. |
| `cqrs.rabbitmq.retry.max-attempts` | `int` | `3` | Maximum number of retry attempts before a message is sent to the dead-letter queue. |
| `cqrs.rabbitmq.retry.ttl` | `long` | `1000` | Time-to-live (in milliseconds) for messages in the retry queue before they are re-delivered. |
| `cqrs.rabbitmq.commands.exchange` | `String` | `"commands"` | Logical name of the command exchange. Combined with prefix to form the full exchange name. |
| `cqrs.rabbitmq.commands.concurrent-consumers` | `int` | `10` | Minimum number of concurrent consumers for the command listener container. |
| `cqrs.rabbitmq.commands.max-concurrent-consumers` | `int` | `20` | Maximum number of concurrent consumers for the command listener container. |
| `cqrs.rabbitmq.events.exchange` | `String` | `"events"` | Logical name of the event exchange. |
| `cqrs.rabbitmq.events.concurrent-consumers` | `int` | `10` | Minimum number of concurrent consumers for the event listener container. |
| `cqrs.rabbitmq.events.max-concurrent-consumers` | `int` | `20` | Maximum number of concurrent consumers for the event listener container. |
| `cqrs.rabbitmq.queries.exchange` | `String` | `"queries"` | Logical name of the query exchange. |
| `cqrs.rabbitmq.queries.concurrent-consumers` | `int` | `10` | Minimum number of concurrent consumers for the query listener container. |
| `cqrs.rabbitmq.queries.max-concurrent-consumers` | `int` | `20` | Maximum number of concurrent consumers for the query listener container. |

### Exchange and Queue Naming

The full exchange and queue names are derived from the prefix, application name, and exchange name:

```
Exchange:       {rabbitmq.prefix}.{bus.exchange}
Queue:          {rabbitmq.prefix}.{spring.application.name}.{bus.exchange}
Retry Exchange: {rabbitmq.prefix}.{bus.exchange}.retry
Retry Queue:    {rabbitmq.prefix}.{spring.application.name}.{bus.exchange}.retry
DL Exchange:    {rabbitmq.prefix}.{bus.exchange}.dead_letter
DL Queue:       {rabbitmq.prefix}.{spring.application.name}.{bus.exchange}.dead_letter
```

The application name is read from `spring.application.name` (defaults to `cqrs-app` if not set).

## Full YAML Example

```yaml
spring:
  application:
    name: order-service
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

cqrs:
  naming:
    prefix: order-service
  validation:
    enabled: true
  observability:
    enabled: true
  rabbitmq:
    enabled: true
    prefix: order-service
    retry:
      max-attempts: 5
      ttl: 2000
    commands:
      exchange: commands
      concurrent-consumers: 5
      max-concurrent-consumers: 15
    events:
      exchange: events
      concurrent-consumers: 10
      max-concurrent-consumers: 30
    queries:
      exchange: queries
      concurrent-consumers: 3
      max-concurrent-consumers: 10
```

With this configuration, the following RabbitMQ resources are created:

| Resource | Name |
|---|---|
| Command Exchange | `order-service.commands` |
| Command Queue | `order-service.order-service.commands` |
| Command Retry Exchange | `order-service.commands.retry` |
| Command Retry Queue | `order-service.order-service.commands.retry` |
| Command DL Exchange | `order-service.commands.dead_letter` |
| Command DL Queue | `order-service.order-service.commands.dead_letter` |
| Event Exchange | `order-service.events` |
| Event Queue | `order-service.order-service.events` |
| Query Exchange | `order-service.queries` |
| Query Queue | `order-service.order-service.queries` |

## Minimal YAML Example

For in-process-only usage (no RabbitMQ):

```yaml
cqrs:
  naming:
    prefix: my-app
```

Everything else uses defaults. The buses work in-process with `SpringCommandBus`, `SpringEventBus`, and `SpringQueryBus`.

To explicitly disable RabbitMQ when the dependency is on the classpath:

```yaml
cqrs:
  rabbitmq:
    enabled: false
```

## Spring Boot 4 Notes

The configuration properties are the same for Spring Boot 4. Use `spring-boot-cqrs-boot4-starter` instead of `spring-boot-cqrs-boot3-starter` in your dependency declaration. Spring Boot 4 (4.0.x) uses the same `cqrs.*` property namespace, so existing configuration files work without changes when migrating from Boot 3 to Boot 4.
