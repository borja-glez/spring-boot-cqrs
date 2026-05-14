# Configuration Reference

All configuration properties use the `cqrs.*` prefix and are managed through Spring Boot's `@ConfigurationProperties` mechanism. The properties are identical for both Spring Boot 3 (boot3-starter) and Spring Boot 4 (boot4-starter).

## Table of Contents

- [Core Properties](#core-properties)
- [Context Propagation Properties](#context-propagation-properties)
- [Tracing Properties](#tracing-properties)
- [Actuator Endpoints](#actuator-endpoints)
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
| `cqrs.context.enabled` | `boolean` | `true` | Enables the `ContextPropagationMiddleware` (correlation ID + MDC + transport headers). Requires SLF4J on the classpath. |
| `cqrs.context.auto-correlation-id` | `boolean` | `true` | If no `correlationId` is present on entry, generates a UUID and adds it to the current `MessageContext`. |
| `cqrs.context.mdc-keys` | `List<String>` | `[correlationId]` | Context keys mirrored into SLF4J MDC during handler execution. Previous MDC values are restored on exit. |
| `cqrs.context.header-prefix` | `String` | `"cqrs.context."` | Prefix applied to transport headers (RabbitMQ + Kafka) when serializing/deserializing the context across services. |
| `cqrs.tracing.enabled` | `boolean` | `true` | Enables the `TracingMiddleware` (wraps each dispatch in a Micrometer `Observation`). Requires an `ObservationRegistry` bean (provided by Spring Boot Actuator). |
| `cqrs.tracing.observation-name` | `String` | `"cqrs.bus.dispatch"` | Name of the observation/span created around each bus dispatch. |

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

## Context Propagation Properties

```yaml
cqrs:
  context:
    enabled: true                 # default
    auto-correlation-id: true     # default
    mdc-keys:                     # default: [correlationId]
      - correlationId
      - tenantId
    header-prefix: "cqrs.context."  # default
```

When enabled (the default), the `ContextPropagationMiddleware` is installed with `Ordered.HIGHEST_PRECEDENCE`. It:

- Reads the current `MessageContext` from a `ThreadLocal` on dispatch entry.
- Generates a `correlationId` (UUID) when missing and `auto-correlation-id=true`.
- Mirrors every key listed in `mdc-keys` into SLF4J MDC (restoring previous values on exit).
- Serializes context entries into RabbitMQ/Kafka message headers on publish (prefixed by `header-prefix`) and rehydrates them on the consumer side before the middleware chain runs.

Set `cqrs.context.enabled=false` to disable auto-registration of the `ContextPropagationMiddleware`. This turns off the ThreadLocal/MDC middleware behavior described above, but it does **not** by itself disable RabbitMQ/Kafka transport header propagation; those adapters still serialize the current `MessageContext` into outbound headers and rehydrate it on inbound messages, independently of the middleware. See [middleware.md](middleware.md#message-context--correlation-id) for usage details and code examples.

## Tracing Properties

```yaml
cqrs:
  tracing:
    enabled: true                      # default
    observation-name: "cqrs.bus.dispatch"  # default
```

When enabled (the default), and an `ObservationRegistry` bean is present in the context (provided by Spring Boot Actuator), the `TracingMiddleware` is installed with `Ordered.HIGHEST_PRECEDENCE + 10`. It wraps every bus dispatch in a Micrometer `Observation` named after `observation-name`, with low-cardinality key-values `cqrs.message.kind` (one of `command` / `event` / `query` / `unknown`) and `cqrs.message.type` (the message class's simple name).

When Micrometer Tracing is also on the classpath (e.g., via `micrometer-tracing-bridge-otel` + an exporter), the observation becomes a span and stitches into the active trace. See [middleware.md](middleware.md#distributed-tracing) for the complete wiring guide and cross-transport propagation notes.

## Actuator Endpoints

This library does not introduce its own `cqrs.actuator.*` properties. When `spring-boot-starter-actuator` is on the classpath, the boot3 and boot4 starters expose `/actuator/cqrs` and contribute a `cqrs` section to `/actuator/info`. Standard Spring Boot management properties control them:

- `management.endpoint.cqrs.enabled` — toggle the `/actuator/cqrs` endpoint (default `true`).
- `management.info.cqrs.enabled` — toggle the info contributor (default `true`).
- `management.endpoints.web.exposure.include` — expose `cqrs` and `info` for HTTP access.

See [actuator.md](actuator.md) for the full guide, response payloads and transport health notes.

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
  context:
    enabled: true
    auto-correlation-id: true
    mdc-keys:
      - correlationId
      - tenantId
    header-prefix: "cqrs.context."
  tracing:
    enabled: true
    observation-name: "cqrs.bus.dispatch"
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
