# Upgrading

This guide lists the changes that need action when moving from one minor version to the next. The
full list of changes of each release is in [CHANGELOG.md](../CHANGELOG.md), where breaking entries
are marked with **BREAKING:**.

## Upgrading to 0.6.0

0.6.0 moves the public API to a new module and has two breaking changes in it. A Spring Boot
application whose handlers live under its own packages usually only has to replace its
`ParameterizedTypeReference` arguments.

1. A new artifact, `spring-boot-cqrs-api`, holds the public API
   ([framework-free API](#the-public-api-moves-to-spring-boot-cqrs-api)).
2. `CommandBus` and `QueryBus` take a `ResultType` instead of a `ParameterizedTypeReference`
   ([ResultType](#resulttype-replaces-parameterizedtypereference)).
3. `@CommandHandler`, `@QueryHandler` and `@EventHandler` are no longer `@Service` stereotypes
   ([handler registration](#handler-annotations-are-no-longer-service)).
4. Three behaviour changes may need action: the PostgreSQL outbox index, `@Idempotent` event
   handlers delivered after commit, and a `@Bean` that clashes with a scanned handler
   ([Behaviour changes](#behaviour-changes)).

### The public API moves to `spring-boot-cqrs-api`

Issue #125.

The messages (`Command`, `Event`, `Query`, `AggregateRoot`, `KeyedMessage`), the bus interfaces
(`CommandBus`, `QueryBus`, `EventBus`), the new `ResultType` and the annotations (`@CommandHandler`,
`@HandleCommand`, `@QueryHandler`, `@HandleQuery`, `@EventHandler`, `@HandleEvent`, `@CqrsMessage`,
`@Idempotent`) now live in `com.borjaglez.cqrs:spring-boot-cqrs-api`, a module without dependencies.
Their packages do not change, so no import changes, and `spring-boot-cqrs-core` (and therefore every
starter and adapter) brings the module in.

Migration:

- Nothing, unless you want a module free of Spring: a domain or application module can now depend on
  `spring-boot-cqrs-api` alone.
- The API and the core share packages, which works on the classpath but not as named JPMS modules.

### `ResultType` replaces `ParameterizedTypeReference`

Issue #125.

`CommandBus.dispatchAndReceive(Command, ParameterizedTypeReference<R>)` and
`QueryBus.ask(Query, ParameterizedTypeReference<R>)` are replaced by overloads that take a
`com.borjaglez.cqrs.ResultType<R>`, so the bus interfaces no longer depend on Spring. It is captured
the same way, and the RabbitMQ and Kafka buses still rebuild a generic result such as a
`List<OrderDto>` with typed elements.

Before (0.5.x):

```java
List<OrderDto> orders = queryBus.ask(new ListOrders(), new ParameterizedTypeReference<List<OrderDto>>() {});
```

After (0.6.0):

```java
List<OrderDto> orders = queryBus.ask(new ListOrders(), new ResultType<List<OrderDto>>() {});
```

Migration:

- Replace `new ParameterizedTypeReference<...>() {}` with `new ResultType<...>() {}` in the calls to
  `dispatchAndReceive` and `ask`, and `ParameterizedTypeReference.forType(type)` with
  `ResultType.of(type)`.
- A `CommandBus` or `QueryBus` of your own that overrides the typed overload must change its
  signature to take a `ResultType<R>`; `ParameterizedTypeReference.forType(resultType.type())`
  converts it for Spring APIs.
- `MessageSerializer.deserialize(byte[], ParameterizedTypeReference<T>)` does not change.

### Handler annotations are no longer `@Service`

Issue #125.

`@CommandHandler`, `@QueryHandler` and `@EventHandler` used to be meta-annotated with `@Service`, so
the application's component scan registered handler classes as beans. They are now plain
annotations. Since 0.6.0 the starters register the handler classes found in the application packages
themselves (see [Handler registration](core.md#handler-registration)), so most applications are not
affected:

| Your application | What to do |
|------------------|------------|
| Spring Boot, handlers under the package of the `@SpringBootApplication` class or of any `@ComponentScan` | Nothing: the starter registers them. |
| Spring Boot, handlers outside those packages, or `cqrs.handlers.scan-enabled=false` | Declare each handler with `@Component`, a `@Bean` method or `@Import`. |
| Spring Boot, handlers left out with `@ComponentScan(excludeFilters = ...)` | The handler scan ignores `excludeFilters` (it applies only Spring Boot's `TypeExcludeFilter`), so they are now registered. Use a condition on the handler (`@Profile`, `@ConditionalOnProperty`, ...), or set `cqrs.handlers.scan-enabled=false` and register the handlers you want. |
| Spring Boot, `scanBasePackages` narrower than the package of the `@SpringBootApplication` class | The handler scan also covers that package (it is the auto-configuration package), so its handlers are now registered. Move them, use a condition, or set `cqrs.handlers.scan-enabled=false`. |
| Without Spring Boot (only `spring-boot-cqrs-core`) | Add `@EnableCqrsHandlers` to a configuration class, or declare the handlers as beans. |

Code that found handlers through the stereotype, such as `getBeansWithAnnotation(Service.class)` or an
AOP pointcut on `@within(org.springframework.stereotype.Service)`, no longer matches them; match the
handler annotation instead.

To check after upgrading that every handler is registered, set
`cqrs.introspection.log-handlers-on-startup=true` and compare the startup line
`CQRS handlers registered: N commands, N events, N queries, ...` with the one of the previous version.
At `DEBUG` level the `com.borjaglez.cqrs.introspection.CqrsIntrospectionLogger` logger also lists
each handler.

### Behaviour changes

- **PostgreSQL outbox index.** The outbox metrics read the unpublished rows with a query that a new
  partial index serves. If you manage the outbox schema yourself (Flyway, Liquibase) and did not
  take the DDL from the library, add it, replacing `<table>` with your outbox table name (the
  default is `cqrs_outbox`):

  ```sql
  CREATE INDEX IF NOT EXISTS <table>_unpublished ON <table> (created_at) WHERE published_at IS NULL;
  ```

  The metrics are on whenever Micrometer is present; set `cqrs.outbox.metrics.enabled=false` to turn
  them off. H2 needs nothing extra.
- **`@Idempotent` event handlers delivered after commit** run in their own transaction (`REQUIRES_NEW`)
  with the JDBC store, also when `TransactionMiddleware` is off. While the event is delivered, that
  transaction holds one more pooled connection than before per nesting level (the transaction that
  published the event keeps its connection, now suspended). Size the connection pool accordingly,
  or set `cqrs.events.transactional=false` to deliver the event in the publishing transaction.
  See [Idempotency](idempotency.md#handlers-without-transactional).
- **A `@Bean` that clashes with a scanned handler fails startup.** A `@Bean` whose name equals the
  default bean name of a scanned handler, but whose declared return type is a supertype or an
  interface of the handler, now fails startup with an `IllegalStateException` that names the bean
  and the handler class. In 0.5 it silently replaced the component. Rename the `@Bean`, or register
  the handler yourself under another name.

## Upgrading to 0.4.0

0.4.0 has five breaking changes, all in the RabbitMQ and Kafka adapters. Applications that only use
the in-process buses need no change, unless they construct or deconstruct the registry and
introspection records (see [Records with new components](#records-with-new-components)). Most
applications that use a broker only need the first three checks:

1. RabbitMQ consumes only messages annotated with `@CqrsMessage` by default
   ([exposed messages](#rabbitmq-exposes-only-cqrsmessage-messages-by-default)).
2. `RabbitMqEventBus` and `KafkaEventBus` throw when an event cannot be sent, instead of delivering
   it to the local event bus ([RabbitMQ](#rabbitmqeventbus-rethrows-broker-failures),
   [Kafka](#kafkaeventbus-rethrows-broker-failures)).
3. Failed Kafka records are retried with back-off and then published to a per-application
   dead-letter topic ([Kafka retry](#kafka-consumers-retry-and-dead-letter-failed-records)).

The last change moves validation and the correlation id of remote messages to the sending side
([outbound middleware](#validation-and-context-run-on-the-sender-of-remote-buses)).

### RabbitMQ exposes only `@CqrsMessage` messages by default

Pull request #103.

Up to 0.3.x every handled command, query and event was bound to the application's RabbitMQ queues
and consumed. The new property `cqrs.rabbitmq.expose` (`annotated` or `all`) defaults to
`annotated`: only messages annotated with `@CqrsMessage` are bound and consumed. Other messages stay
local. If one reaches the queue anyway (through the default exchange, or through a binding left by
an earlier version), the consumer logs a `WARN` and rejects it without requeue and without retry, so
it never reaches the handler; a request that expects a reply is answered with an error, which the
requester sees as a `RemoteHandlerException`. Local dispatch through `SpringCommandBus`,
`SpringQueryBus` and `springEventBus` is not affected.

The handler annotations (`@CommandHandler`, `@EventHandler`, `@QueryHandler`, `@HandleCommand`,
`@HandleEvent`, `@HandleQuery`) gain a `remote` attribute, `true` by default. `remote = false` keeps a
handler local even when its message is annotated; a handler is remote only when both its class and
its method allow it. For an exposed event received from the broker only the remote handlers run.

Before (0.3.x, every handled message was consumed from RabbitMQ):

```java
public class ReserveStockCommand extends Command { ... }
```

After (annotate the messages other services send or publish):

```java
@CqrsMessage(service = "inventory", module = "stock", name = "reserve-stock")
public class ReserveStockCommand extends Command { ... }
```

After (keep the old behaviour explicitly):

```yaml
cqrs:
  rabbitmq:
    expose: all
```

Migration:

- Annotate with `@CqrsMessage` every command, query and event that another service sends or
  publishes to this application over RabbitMQ, or set `cqrs.rabbitmq.expose=all`.
- Mark handlers that must never be triggered from the broker with `remote = false`.
- RabbitMQ keeps the bindings of a durable queue when the application stops declaring them, so the
  bindings of messages that are no longer exposed remain: the consumer rejects what they deliver, and
  you can remove them from the management UI or with `rabbitmqadmin`.
- The `RabbitMqCommandConsumer`, `RabbitMqQueryConsumer` and `RabbitMqEventConsumer` constructors
  without a `RabbitMqExposure` argument now accept only annotated messages (`RabbitMqExposure.ANNOTATED`).
  Pass `RabbitMqExposure.ALL` when you build a consumer yourself and need the previous behaviour.

See [Exposed and local messages](rabbitmq-adapter.md#exposed-and-local-messages).

### `RabbitMqEventBus` rethrows broker failures

Pull request #101.

`RabbitMqEventBus.publish(event)` used to catch the `AmqpException` of a failed send and publish the
event to the local Spring event bus, so the caller believed the event had left the process when no
other service would see it. It now rethrows the `AmqpException` unchanged and local handlers do not
run. `publish(List<Event>)` publishes in order and stops at the first failure.

The constructor lost its fallback argument:

| Before (0.3.x) | After (0.4.0) |
|----------------|---------------|
| `new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, exchangeName, fallbackEventBus)` | `new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, exchangeName)` |
| | `new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, exchangeName, confirmTimeout)` (publisher confirms, `null` for none) |
| | `new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, exchangeName, confirmTimeout, middlewares)` (see [outbound middleware](#validation-and-context-run-on-the-sender-of-remote-buses)) |

By default the bus still does not wait for the broker to accept the event. The new opt-in publisher
confirms make `publish` wait for the confirmation and throw `PublishNotConfirmedException` (an
`AmqpException`) when the broker rejects the event or does not confirm it in time:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated   # required, the application fails to start without it
cqrs:
  rabbitmq:
    events:
      confirms:
        enabled: true
        timeout: 5s
```

`cqrs.rabbitmq.events` is now bound to `RabbitMqCqrsProperties.EventBusProperties`, a subclass of
`BusProperties` that adds `confirms`; commands and queries keep `BusProperties`.

Migration:

- Remove the fallback `EventBus` argument where you build a `RabbitMqEventBus` yourself.
- Callers of `RabbitMqEventBus.publish` that relied on the silent local delivery must handle the
  exception, or publish through an outbox (see
  [Transactional event publishing](../README.md#transactional-event-publishing)).
- Code that calls `RabbitMqCqrsProperties.setEvents(...)` must pass an `EventBusProperties`.

### `KafkaEventBus` rethrows broker failures

Pull request #98.

`KafkaEventBus.publish(event)` used to catch any `RuntimeException` from `KafkaMessagePublisher` and
publish the event to the local `springEventBus`. It now rethrows the publisher's exception unchanged
(the publisher waits for the broker acknowledgement) and never delivers the event locally on failure.
`publish(List<Event>)` publishes in order and stops at the first failure.
`KafkaEventBusAutoConfiguration` no longer injects `springEventBus` into the bus.

| Before (0.3.x) | After (0.4.0) |
|----------------|---------------|
| `new KafkaEventBus(publisher, topicNamingStrategy, topicName, fallbackEventBus)` | `new KafkaEventBus(publisher, topicNamingStrategy, topicName)` |
| | `new KafkaEventBus(publisher, topicNamingStrategy, topicName, middlewares)` (see [outbound middleware](#validation-and-context-run-on-the-sender-of-remote-buses)) |

Migration:

- Remove the fallback `EventBus` argument where you build a `KafkaEventBus` yourself.
- Callers of `KafkaEventBus.publish` that relied on the silent local delivery must handle the
  exception, or publish through an outbox (see
  [Transactional event publishing](../README.md#transactional-event-publishing)).

### Kafka consumers retry and dead-letter failed records

Pull request #102.

Up to 0.3.x the command, event and query listener containers used Spring Kafka's default error
handler: a failed record was delivered ten times without back-off and then logged and skipped. They
now get an error handler configured from `cqrs.kafka.error-handling.*`:

- `max-attempts` deliveries in total (default `3`), with exponential back-off
  (`back-off.initial-interval=1s`, `back-off.multiplier=2.0`, `back-off.max-interval=10s`);
- then the record is published to the dead-letter topic of the failing application and bus,
  `{prefix}.{spring.application.name}.{bus-topic}.dlt` (for example `cqrs.orders-service.events.dlt`),
  with the original headers, Spring's `kafka_dlt-*` headers and the `cqrs.error.type`,
  `cqrs.error.message`, `cqrs.error.attempts` and `cqrs.error.timestamp` headers shared with the
  RabbitMQ dead-letter queues;
- with `cqrs.kafka.auto-create-topics=true` (the default) the dead-letter topics are declared as
  `NewTopic` beans (`cqrsCommandsDeadLetterTopic`, `cqrsEventsDeadLetterTopic`,
  `cqrsQueriesDeadLetterTopic`), sized by `dead-letter.partitions` and `dead-letter.replicas`.

A record with no `cqrs.payload.type` header, or whose payload cannot be deserialized, now fails with
`UnprocessableRecordException` (package `com.borjaglez.cqrs.kafka.consumer`). It extends
`IllegalStateException`, the type thrown before, so existing `catch` blocks still match; it is not
retried and goes straight to the dead-letter topic.

Request/reply commands and queries whose handler fails are still answered with an error reply and
are neither retried nor dead-lettered.

To keep log-and-skip (after the configured attempts, not ten immediate ones):

```yaml
cqrs:
  kafka:
    error-handling:
      dead-letter:
        enabled: false
```

Migration:

- Make sure the application can create or write the `*.dlt` topics (broker ACLs, topic creation
  policies), or create them beforehand, or disable dead-lettering.
- A `CommonErrorHandler` bean of the application replaces the module's handler on the three
  containers: the bean named `cqrsKafkaErrorHandler` first, otherwise the unique or `@Primary` one.
  If the application already defines a `CommonErrorHandler` for its own `@KafkaListener`s, it now
  also applies to the CQRS containers; name a dedicated bean `cqrsKafkaErrorHandler` to separate
  them.
- A custom `KafkaTopicNamingStrategy` keeps compiling: `deadLetterTopic(applicationName,
  logicalName)` is a default method. Override it to name the dead-letter topics differently.
- `RetryMiddleware` (see [New features](#new-features)) runs inside each delivery, so the attempts
  multiply: `cqrs.retry.max-attempts` x `cqrs.kafka.error-handling.max-attempts`.

See [Retry and Dead-Letter Topics](kafka-adapter.md#retry-and-dead-letter-topics).

### Validation and context run on the sender of remote buses

Pull request #106.

Up to 0.3.x no middleware ran on the sending side of the RabbitMQ and Kafka buses. `BusMiddleware`
has a new method, `default Set<DispatchPhase> phases()`, and `DispatchPhase` has three values:
`LOCAL` (in-process buses), `OUTBOUND` (the sending side of the RabbitMQ and Kafka buses, before the
message is published) and `INBOUND` (their consumers, before the handler). The default is `LOCAL` and
`INBOUND`, the previous behaviour, so user middleware is not affected.

`ContextPropagationMiddleware` and `CommandValidationInterceptor` now declare all three phases:

- An invalid command sent through `RabbitMqCommandBus` or `KafkaCommandBus` (`dispatch`,
  `dispatchAndWait`, `dispatchAndReceive`) throws `jakarta.validation.ConstraintViolationException`
  in the caller and is not published. Before, it travelled to the receiver and the caller got a
  remote error (`RemoteHandlerException` with RabbitMQ, `RuntimeException("Remote handler error:
  ...")` with Kafka) or, with a fire-and-forget `dispatch`, nothing at all.
- A remote message sent without an open `MessageContext` carries a `cqrs.context.correlationId`
  generated by the sender (with `cqrs.context.auto-correlation-id=true`), so the sender and the
  receiver log the same id.

`TracingMiddleware`, `MicrometerBusObservability` and `RetryMiddleware` keep `LOCAL` and `INBOUND`.

Before:

```java
try {
    rabbitMqCommandBus.dispatchAndReceive(invalidCommand);
} catch (RemoteHandlerException ex) {
    // the receiver rejected the command
}
```

After:

```java
try {
    rabbitMqCommandBus.dispatchAndReceive(invalidCommand);
} catch (ConstraintViolationException ex) {
    // rejected in the caller; nothing was published
}
```

Migration:

- Catch `ConstraintViolationException` where you caught the remote validation error.
- To keep the previous behaviour for one of the two middlewares, turn it off
  (`cqrs.validation.enabled=false` or `cqrs.context.enabled=false`) and declare your own bean: a
  subclass whose `phases()` returns `Set.of(DispatchPhase.LOCAL, DispatchPhase.INBOUND)`.
  `cqrs.context.enabled=false` also stops the registration of `MessageContextThreadLocalAccessor`.
- The auto-configurations pass every `BusMiddleware` bean to the remote buses. When you build a
  `RabbitMq*Bus` or `Kafka*Bus` yourself, pass the middlewares to the constructor that takes a
  `List<BusMiddleware>`; the constructors without it run no middleware before sending.
- A middleware that throws on the `OUTBOUND` phase stops the send: nothing is published.

See [Middleware on Remote Buses](middleware.md#middleware-on-remote-buses).

### Records with new components

The registry and introspection records gained components. Their previous constructors are kept, so
constructor calls still compile; record patterns and deconstruction must add the new components:

| Record | New components |
|--------|----------------|
| `CommandHandlerRegistry.HandlerInfo` | `remote` (#103) |
| `QueryHandlerRegistry.HandlerInfo` | `remote` (#103) |
| `EventHandlerRegistry.HandlerInfo` | `condition` (`EventHandlerCondition`, #96), `remote` (#103) |
| `HandlerDescriptor` | `remote` (#103) |
| `MiddlewareDescriptor` | `phases` (#106) |

The `cqrs` actuator endpoint shows `remote` for each handler and `phases` for each middleware.

### New features

Non-breaking changes in 0.4.0 worth knowing when upgrading:

- **Enable each RabbitMQ bus** (#95): `cqrs.rabbitmq.commands.enabled`, `cqrs.rabbitmq.queries.enabled`
  and `cqrs.rabbitmq.events.enabled` (default `true`), like the Kafka module. A disabled bus has no
  bus bean, no queues or exchanges and no listener container, so the application can neither send
  nor receive that kind of message over RabbitMQ. See
  [Enabling each bus](rabbitmq-adapter.md#enabling-each-bus).
- **Conditional event handlers** (#96): `@HandleEvent(condition = "...")` takes a SpEL expression
  evaluated per event, with the event as root object and `#event` variable, and `@beanName`
  references. A malformed expression fails startup; a `null` or non-boolean result raises
  `EventHandlerExecutionException`. See [Conditional event handlers](core.md#conditional-event-handlers).
- **Kafka keys from `KeyedMessage`** (#97): commands and events that implement
  `com.borjaglez.cqrs.KeyedMessage` (`String messageKey()`) are keyed by that key, so all the messages
  of one aggregate land on the same partition, and the key is sent as the `cqrs.message.key` header.
  A `null` or blank key, and every query, fall back to `cqrs.kafka.partition-key.strategy`
  (`MESSAGE_NAME` by default). See
  [Keying by entity with `KeyedMessage`](kafka-adapter.md#keying-by-entity-with-keyedmessage).
- **Retry middleware** (#99): `RetryMiddleware`, `RetryPolicy` and `BackoffStrategy` (package
  `com.borjaglez.cqrs.retry`) retry failed command and query dispatches in process with fixed,
  exponential or jittered back-off. Opt-in with `cqrs.retry.enabled=true`; see the `cqrs.retry.*`
  properties in [Retry Properties](configuration.md#retry-properties) and
  [RetryMiddleware](middleware.md#retrymiddleware).
- **`MessageContext` across threads** (#100): `MessageContext.wrap(Runnable)` /
  `wrap(Callable)` and `MessageContextTaskDecorator` carry the caller's context into executor and
  `@Async` tasks. With `io.micrometer:context-propagation` on the classpath the starters register a
  `MessageContextThreadLocalAccessor`, so Spring's `ContextPropagatingTaskDecorator` carries it too.
  See [Crossing threads](middleware.md#crossing-threads-async-executors).
