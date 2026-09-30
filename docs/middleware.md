# Middleware Documentation

The middleware pipeline intercepts every message dispatched through any of the three buses (Command, Event, Query). Middleware can inspect, modify, or short-circuit messages before they reach handlers. Each middleware declares in which [dispatch phases](#middleware-on-remote-buses) it runs: in local dispatches, on the sending side of the RabbitMQ and Kafka buses, and on their receiving side. By default it runs in local dispatches and on the receiving side.

## Table of Contents

- [BusMiddleware Interface](#busmiddleware-interface)
- [MiddlewareChain](#middlewarechain)
- [Creating Custom Middleware](#creating-custom-middleware)
- [Ordering with @Order](#ordering-with-order)
- [Middleware on Remote Buses](#middleware-on-remote-buses)
- [Built-in Middleware](#built-in-middleware)
- [Message Context & Correlation ID](#message-context--correlation-id)
- [Examples](#examples)

## BusMiddleware Interface

```java
@FunctionalInterface
public interface BusMiddleware {
    Object process(Object message, MiddlewareChain chain) throws Exception;

    default Set<DispatchPhase> phases() {
        return Set.of(DispatchPhase.LOCAL, DispatchPhase.INBOUND);
    }
}
```

The `message` parameter is the `Command`, `Event`, or `Query` being dispatched. Call `chain.proceed(message)` to pass the message to the next middleware in the chain (or to the terminal handler if this is the last middleware). Return the result from `chain.proceed()` to propagate the handler's return value.

`phases()` says where the middleware runs; see [Middleware on Remote Buses](#middleware-on-remote-buses).

## MiddlewareChain

```java
public interface MiddlewareChain {
    Object proceed(Object message) throws Exception;
}
```

The chain advances through the ordered list of middleware, and the final step invokes the actual handler via the registry. The `DefaultMiddlewareChain` implementation tracks the current index and delegates to either the next middleware or the terminal handler.

```java
public class DefaultMiddlewareChain implements MiddlewareChain {
    public DefaultMiddlewareChain(List<BusMiddleware> middlewares, MiddlewareChain terminal);
    public Object proceed(Object message) throws Exception;
}
```

## Creating Custom Middleware

To create a middleware:

1. Implement `BusMiddleware`.
2. Register it as a Spring bean (`@Component` or `@Bean`).
3. Optionally annotate with `@Order` to control execution position.

```java
@Component
@Order(10)
public class MyMiddleware implements BusMiddleware {

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
        // Before handler
        doSomethingBefore(message);

        try {
            Object result = chain.proceed(message);

            // After handler (success)
            doSomethingAfter(message, result);
            return result;

        } catch (Exception e) {
            // After handler (failure)
            doSomethingOnError(message, e);
            throw e;
        }
    }
}
```

You can short-circuit the chain by not calling `chain.proceed()`:

```java
@Override
public Object process(Object message, MiddlewareChain chain) throws Exception {
    if (!isAuthorized(message)) {
        throw new AccessDeniedException("Not authorized");
    }
    return chain.proceed(message);
}
```

You can also modify the message before passing it along:

```java
@Override
public Object process(Object message, MiddlewareChain chain) throws Exception {
    Object enrichedMessage = enrich(message);
    return chain.proceed(enrichedMessage);
}
```

## Ordering with @Order

Middleware beans are collected by Spring and ordered using `@Order`. Lower values execute first.

```
@Order(1)  LoggingMiddleware          -- executes first
@Order(2)  AuthorizationMiddleware    -- executes second
@Order(3)  TransactionalMiddleware    -- executes third
           [Handler]                  -- executes last
```

Middleware without `@Order` receives the default order value (`Ordered.LOWEST_PRECEDENCE`), meaning it runs after all explicitly ordered middleware.

The built-in middleware participates in the same pipeline: `ContextPropagationMiddleware` (`HIGHEST_PRECEDENCE`), `TracingMiddleware` (`HIGHEST_PRECEDENCE + 10`), the opt-in `RetryMiddleware` (`LOWEST_PRECEDENCE - 100`), and `CommandValidationInterceptor` and `MicrometerBusObservability` (no `@Order`, so lowest precedence).

## Middleware on Remote Buses

A message can pass a middleware chain in three places, the three values of `DispatchPhase`:

| Phase | Where | Buses |
|---|---|---|
| `LOCAL` | In-process dispatch, before the handler | `SpringCommandBus`, `SpringQueryBus`, the local `springEventBus` |
| `OUTBOUND` | Sending side of a remote bus, before the message is published | `RabbitMqCommandBus`, `RabbitMqQueryBus`, `RabbitMqEventBus`, `KafkaCommandBus`, `KafkaQueryBus`, `KafkaEventBus` |
| `INBOUND` | Receiving side of a remote bus, in the consumer, before the handler | The RabbitMQ and Kafka consumers |

Every bus is given all the `BusMiddleware` beans, in `@Order` order, and runs only those whose `phases()` contains its phase. A remote message therefore passes the `OUTBOUND` chain in the sending process and the `INBOUND` chain in the receiving process; the consumer rehydrates the `MessageContext` from the headers before its chain runs. A middleware that declares both phases runs twice per remote message, once in each process.

`BusMiddleware.phases()` defaults to `LOCAL` and `INBOUND`: a middleware runs where the handler runs and not on the sender. The built-in middleware:

| Middleware | `LOCAL` | `OUTBOUND` | `INBOUND` | Why |
|---|:-:|:-:|:-:|---|
| `ContextPropagationMiddleware` | Yes | Yes | Yes | On the sender it generates the correlation id before the headers are written and mirrors the context into MDC during the send. |
| `CommandValidationInterceptor` | Yes | Yes | Yes | An invalid command fails on the sender, before anything is published. The receiver validates again: it cannot trust every producer. |
| `TracingMiddleware` | Yes | No | Yes | The sender's span is the transport's own producer observation; a second one would duplicate it. |
| `MicrometerBusObservability` | Yes | No | Yes | No sender-side meter: sharing the `cqrs.bus.dispatch` name would count each remote message twice. |
| `RetryMiddleware` | Yes | No | Yes | It retries the handler. A failed remote send is not retried by it. |

Consequences on the sending side:

- **Validation:** `dispatch`, `dispatchAndWait` and `dispatchAndReceive` of an invalid command throw `jakarta.validation.ConstraintViolationException` in the caller, and nothing is published. Before 0.4.0 the command travelled to the receiver and the caller got a remote error (`RemoteHandlerException` with RabbitMQ, `RuntimeException("Remote handler error: ...")` with Kafka) or, with a fire-and-forget `dispatch`, nothing at all.
- **Context:** a remote dispatch made without an open `MessageContext` gets a correlation id on the sender (with `cqrs.context.auto-correlation-id=true`), sent as `cqrs.context.correlationId`, so the sender and the receiver log the same id. The scope is closed when the send returns: code after the dispatch still sees the caller's context. Open a `MessageContext.Scope` at the boundary (see [Seeding the context](#seeding-the-context-at-a-system-boundary)) when the caller's own logs must carry the id too.
- **Metrics and spans:** `MicrometerBusObservability` and `TracingMiddleware` record the dispatch on the receiver only. On the sender the only observation is the transport's own (`spring.rabbitmq.template.observation-enabled`, `spring.kafka.template.observation-enabled`); see [Distributed Tracing](#distributed-tracing).
- **User middleware** (logging, authorization, transactions, idempotency) keeps running only where the handler runs, unless it declares `OUTBOUND`.

A middleware can throw to stop a remote send, like any other middleware: the publisher is never called, and a checked exception is wrapped in `CommandHandlerExecutionException`, `QueryHandlerExecutionException` or `EventHandlerExecutionException`, as in local dispatches. The message a middleware passes to `chain.proceed` is the one sent, and its class chooses the routing key or message name.

To run your own middleware on the sender, override `phases()`:

```java
@Component
@Order(5)
public class OutboundAuditMiddleware implements BusMiddleware {

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
        audit.recordSend(message);
        return chain.proceed(message);
    }

    @Override
    public Set<DispatchPhase> phases() {
        return Set.of(DispatchPhase.OUTBOUND);   // only before remote sends
    }
}
```

`DispatchPhase.OUTBOUND.select(middlewares)` returns the middlewares of a list that declare a phase, in order; the buses and consumers use it, and `phases()` must not return `null`. The auto-configuration passes every `BusMiddleware` bean to the remote buses; when you build a remote bus yourself, pass them to the constructor that takes a `List<BusMiddleware>` (the constructors without it run no middleware before sending). The `/actuator/cqrs` endpoint lists the phases of each middleware (see [Actuator](actuator.md)).

> **Upgrading from 0.3.x.** Before 0.4.0 no middleware ran on the sending side of the RabbitMQ and Kafka buses. `ContextPropagationMiddleware` and `CommandValidationInterceptor` now also run there: an invalid command fails in the caller with `ConstraintViolationException` instead of a remote error, and remote messages sent without a context carry a correlation id generated by the sender. Catch `ConstraintViolationException` where you caught the remote error. To keep the previous behavior for one of them, declare your own bean of that type, a subclass whose `phases()` returns `Set.of(DispatchPhase.LOCAL, DispatchPhase.INBOUND)`: it replaces the built-in one. User middleware is not affected: `phases()` defaults to the previous behavior.

## Built-in Middleware

### CommandValidationInterceptor

**Package:** `com.borjaglez.cqrs.validation`  
**Auto-configured:** Yes, when JSR-380 (`jakarta.validation`) is on the classpath and a `Validator` bean exists; an application bean of this type replaces it  
**Property:** `cqrs.validation.enabled` (default: `true`)

Validates `Command` instances using the JSR-380 `Validator`. If any constraint violations are found, a `ConstraintViolationException` is thrown before the handler is invoked.

```java
public class CommandValidationInterceptor implements BusMiddleware {
    public Object process(Object message, MiddlewareChain chain) throws Exception {
        if (message instanceof Command) {
            Set<ConstraintViolation<Object>> violations = validator.validate(message);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        }
        return chain.proceed(message);
    }
}
```

Only `Command` instances are validated. Events and queries pass through untouched.

It runs in every phase: in local dispatches, and on both sides of the RabbitMQ and Kafka buses, so an invalid command sent to another service fails in the caller before it is published, and the receiver validates what it gets from other producers (see [Middleware on Remote Buses](#middleware-on-remote-buses)).

Usage:

```java
@Getter
@CqrsMessage(service = "my-app", module = "user", name = "create-user")
public class CreateUserCommand extends Command {
    @NotBlank private final String name;
    @Email private final String email;
    // ...
}
```

### MicrometerBusObservability

**Package:** `com.borjaglez.cqrs.observability`  
**Auto-configured:** Yes, when Micrometer is on the classpath  
**Property:** `cqrs.observability.enabled` (default: `true`)

Records a `cqrs.bus.dispatch` timer for every message handled: local dispatches and, with RabbitMQ and Kafka, messages consumed on the receiving side. Remote sends are not timed on the sender, so each message is counted once. Tags:

| Tag | Description |
|---|---|
| `cqrs.type` | `command`, `event`, `query`, or `unknown` |
| `cqrs.message` | Simple class name of the message (e.g., `CreateOrderCommand`) |
| `cqrs.outcome` | `success` or `error` |

Example Prometheus output:

```
cqrs_bus_dispatch_seconds_count{cqrs_type="command",cqrs_message="CreateOrderCommand",cqrs_outcome="success"} 42.0
cqrs_bus_dispatch_seconds_sum{cqrs_type="command",cqrs_message="CreateOrderCommand",cqrs_outcome="success"} 1.234
```

### ContextPropagationMiddleware

**Package:** `com.borjaglez.cqrs.context`
**Auto-configured:** Yes, when SLF4J (`org.slf4j.MDC`) is on the classpath; an application bean of this type replaces it
**Property:** `cqrs.context.enabled` (default: `true`)
**Order:** `Ordered.HIGHEST_PRECEDENCE` — runs before validation, observability, and any user middleware.

Propagates an immutable `MessageContext` through every bus dispatch. The context carries business-level metadata (correlation ID, tenant ID, user ID, any key/value pair) that handlers and other middleware can read via `MessageContext.current()`. Nothing needs to be threaded through method parameters.

On entry the middleware:

1. Reads the current `MessageContext` from a `ThreadLocal`.
2. If no `correlationId` is present and `cqrs.context.auto-correlation-id=true` (default), generates a UUID and adds it.
3. Mirrors every configured key (see `cqrs.context.mdc-keys`) into SLF4J MDC so downstream logs carry them automatically.
4. Proceeds through the chain.
5. Restores the previous MDC state and context on exit (even if the handler throws).

It runs in every phase. On the sending side of the RabbitMQ and Kafka buses it runs before the message is published, so the correlation id it generates is written to the `cqrs.context.correlationId` header and the receiver continues it.

### RetryMiddleware

**Package:** `com.borjaglez.cqrs.retry`
**Auto-configured:** No, opt-in with `cqrs.retry.enabled=true` (see [Configuration](configuration.md#retry-properties))
**Order:** `RetryMiddleware.ORDER` = `Ordered.LOWEST_PRECEDENCE - 100`

Retries a failed command or query dispatch in process, with a backoff between attempts. Each attempt calls `chain.proceed(message)` again, so every middleware ordered after it and the handler run once per attempt. Because of the order, context propagation and tracing wrap all the attempts (one correlation id, one span), while validation, `MicrometerBusObservability` and unordered user middleware run on each attempt: three attempts produce three `cqrs.bus.dispatch` samples.

- **Commands and queries only.** Events, and any other message, pass through untouched: an event may have several handlers, and retrying the dispatch would run again those that already succeeded.
- **Attempts.** `maxAttempts` counts the first attempt (`1` means no retry), like `cqrs.rabbitmq.retry.max-attempts`. When the policy gives up, the exception of the last attempt is rethrown as is, not wrapped.
- **Classification.** A failure is retriable when the exception, or a cause in its chain, is an instance of a retriable type and neither it nor any cause is an instance of a non-retriable type: non-retriable wins. By default every `RuntimeException` is retriable except `IllegalArgumentException`, `jakarta.validation.ConstraintViolationException`, `CommandNotRegisteredException` and `QueryNotRegisteredException`, so validation failures and missing handlers fail at once. A checked exception thrown by a handler reaches the chain wrapped in `CommandHandlerExecutionException` / `QueryHandlerExecutionException`, a `RuntimeException`, so it is retried unless a cause in the chain is non-retriable; a checked exception thrown by a middleware is classified by its own type (not retried unless listed in `retryOn`).
- **Backoff.** `BackoffStrategy.fixed(delay)`, `exponential(initial, multiplier, max)` (`min(max, initial * multiplier^(failedAttempts - 1))`) or `exponentialWithJitter(initial, multiplier, max, jitterFactor)` (the exponential delay scaled by a random factor in `[1 - jitterFactor, 1 + jitterFactor]`, capped at `max`). The default is exponential with jitter: 100 ms, x2, capped at 5 s, jitter 0.1.
- **Interruption.** If the thread is interrupted while waiting, the interrupt flag is restored and the last handler exception is rethrown (with the `InterruptedException` suppressed).

Defining the middleware yourself (an application bean replaces the auto-configured one):

```java
@Bean
RetryMiddleware retryMiddleware() {
    return RetryMiddleware.builder()
        .defaultPolicy(RetryPolicy.builder()
            .maxAttempts(4)
            .backoff(BackoffStrategy.exponentialWithJitter(
                Duration.ofMillis(50), 2.0, Duration.ofSeconds(2), 0.2))
            .noRetryOn(PaymentDeclinedException.class)
            .build())
        .override(ChargeCardCommand.class, RetryPolicy.builder().maxAttempts(2).build())
        .override(SendEmailCommand.class, RetryPolicy.noRetry())
        .build();
}
```

`RetryPolicy.Builder.retryOn(...)` replaces the retriable types; `noRetryOn(...)` adds to the non-retriable ones. Overrides are looked up by the exact message class; subclasses fall back to the default policy.

`RetryPolicy` and `BackoffStrategy` do not depend on the bus: `policy.shouldRetry(exception, failedAttempts)` and `backoff.delayAfter(failedAttempts)` can drive any retry loop.

**Remote buses.** The middleware keeps the default phases, `LOCAL` and `INBOUND`: with RabbitMQ and Kafka it runs in the consumer (see [Middleware on Remote Buses](#middleware-on-remote-buses)), before the transport's own retry, and not on the sender, so a failed remote send (or a failed remote request/reply) is not retried by it. The attempts multiply: with `cqrs.retry.max-attempts=3` and `cqrs.rabbitmq.retry.max-attempts=3` a failing handler runs up to 9 times before the message is dead-lettered. Lower one of them when you enable both.

**Transactions.** Retrying an optimistic-lock failure only helps when the transaction starts inside the retry: in the handler itself, or in a middleware ordered after `RetryMiddleware`. If the caller's transaction wraps the dispatch, it is already marked rollback-only after the first failure and every retry fails too.

## Message Context & Correlation ID

`MessageContext` is an immutable value object living in `com.borjaglez.cqrs.context`. It is the single place from which handlers read propagated metadata.

### Reading the context inside a handler or middleware

```java
@CommandHandler
public class CreateOrderHandler {

  @HandleCommand
  public OrderId handle(CreateOrderCommand command) {
    MessageContext ctx = MessageContext.current();
    String correlationId = ctx.correlationId();        // auto-generated if missing
    String tenantId = ctx.get("tenantId").orElse("-");
    // ... business logic
  }
}
```

### Seeding the context at a system boundary

Web filters, schedulers, or inbound adapters should open a `Scope` so the data flows through every subsequent dispatch:

```java
MessageContext ctx =
    MessageContext.empty()
        .with(MessageContext.CORRELATION_ID_KEY, request.getHeader("X-Correlation-Id"))
        .with("tenantId", tenantResolver.resolve(request))
        .with("userId", principal.getName());

try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
  commandBus.dispatch(command);
}
```

Nested dispatches (e.g. a command handler publishes an event whose handler dispatches another command) inherit the context automatically because `ContextPropagationMiddleware` runs on every bus and walks the same `ThreadLocal`.

### Cross-transport propagation

Both the RabbitMQ and Kafka adapters serialize every context entry into the headers of every command, query and event they send, one-way (`dispatch`, `publish`) or with a reply (`dispatchAndWait`, `dispatchAndReceive`, `ask`), prefixed by `cqrs.context.header-prefix` (default: `cqrs.context.`). The consuming side rehydrates the context before the middleware chain runs, so the same `correlationId` flows across services without any application code.

| Transport | Header key on the wire |
|---|---|
| RabbitMQ | `cqrs.context.correlationId`, `cqrs.context.tenantId`, … |
| Kafka    | `cqrs.context.correlationId`, `cqrs.context.tenantId`, … |

### Crossing threads (`@Async`, executors)

`MessageContext` lives in a `ThreadLocal`, so it does not follow work handed to another thread on its own. Without help, a task submitted to a `TaskExecutor`, an `@Async` method or `CompletableFuture.supplyAsync(..., executor)` runs with an empty context: messages it sends carry none of the caller's `cqrs.context.*` entries, and both a local dispatch and a remote send get a fresh `correlationId`. Two ways to carry it across:

**1. Explicit, no extra dependency.** `MessageContext.wrap(Runnable)` and `MessageContext.wrap(Callable)` capture the context that is current when you call them and run the task inside it. The worker thread's own context is replaced for the duration of the task and restored afterwards, so pooled threads never leak context between tasks.

```java
executor.submit(MessageContext.wrap(() -> commandBus.dispatch(new ReserveStock(orderId))));

CompletableFuture.runAsync(MessageContext.wrap(() -> notifier.send(order)), executor);
```

To apply it to every task of an executor, set `MessageContextTaskDecorator` as its task decorator:

```java
@Bean
ThreadPoolTaskExecutor cqrsWorkers() {
  ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  executor.setTaskDecorator(new MessageContextTaskDecorator());
  return executor;
}
```

Spring Boot applies a `TaskDecorator` bean to its auto-configured `applicationTaskExecutor` (the one behind `@Async`). Boot 4 composes every `TaskDecorator` bean; Boot 3.5 applies one only when it is the single `TaskDecorator` bean in the context. Declaring `@Bean TaskDecorator messageContextTaskDecorator() { return new MessageContextTaskDecorator(); }` therefore covers `@Async` unless, on Boot 3.5, you already have another decorator (combine them with Spring's `CompositeTaskDecorator` in that case).

**2. Micrometer context-propagation.** When `io.micrometer:context-propagation` is on the classpath (Micrometer Tracing brings it in; otherwise add it yourself), the starters register `MessageContextThreadLocalAccessor` (key `cqrs.messageContext`) in the global `ContextRegistry`. Any executor decorated with Spring's `ContextPropagatingTaskDecorator`, and Reactor with automatic context propagation, then carries `MessageContext` together with the tracing context. With Spring Boot, `spring.task.execution.propagate-context=true` (Boot 4) adds that decorator to the `applicationTaskExecutor`; on Boot 3.5 declare a `ContextPropagatingTaskDecorator` bean. The accessor does not touch MDC: `ContextPropagationMiddleware` mirrors the context into MDC on the next dispatch. The registration is turned off together with the rest of the context support by `cqrs.context.enabled=false`.

Nothing is decorated automatically: code that does not cross threads, and executors you have not decorated, behave as before.

#### Limit: pollers, schedulers and resumed work

Thread propagation only helps when a caller thread hands work to another thread. Work picked up later by a poller, a `@Scheduled` job, a saga runner or an outbox relay has no parent thread whose context could be captured, so it runs with an empty context. For those cases persist the context with the work item and reopen it when the work runs:

```java
// When the work is created
sagaRepository.save(new SagaState(orderId, MessageContext.current().correlationId()));

// When a scheduler/poller picks it up
MessageContext ctx =
    MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, state.correlationId());
try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
  commandBus.dispatch(new ReserveStock(state.orderId()));
}
```

`ContextPropagationMiddleware` also runs on the sending side of the RabbitMQ and Kafka buses (see [Middleware on Remote Buses](#middleware-on-remote-buses)), so a remote dispatch from a context-less thread still sends a `correlationId`, but a new one: it is not linked to the work that started it. Carry the context across threads, or persist it as shown above, to keep one id.

### Logging with MDC

With the default configuration, SLF4J MDC always contains `correlationId` inside the handler execution. A Logback pattern such as:

```xml
<pattern>%d{ISO8601} [%X{correlationId:-}] %-5level %logger{36} - %msg%n</pattern>
```

automatically annotates every log line. Mirror additional keys by setting `cqrs.context.mdc-keys=correlationId,tenantId,userId`.

> **Testing middleware in isolation**: the `spring-boot-cqrs-test` module ships
> `Handlers.invoke(message, handler, middlewares...)`, a tiny harness that runs
> a single handler through a configurable middleware chain without bringing up
> a full bus or Spring context. See [testing.md](testing.md#middleware-test-harness)
> for details.

## Distributed Tracing

`TracingMiddleware` (in `spring-boot-cqrs-core`) wraps every local dispatch, and every message consumed from RabbitMQ or Kafka, in a Micrometer `Observation`. It does not run on the sending side of the remote buses, where the transport's producer observation covers the send. When the consumer wires Micrometer Tracing (e.g., `spring-boot-starter-actuator` + `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`), each dispatch becomes a span named `cqrs.bus.handle` (configurable via `cqrs.tracing.observation-name`) with:

- `cqrs.message.kind` — one of `command`, `event`, `query`, `unknown`
- `cqrs.message.type` — the message class's simple name

> **Changed default:** up to this version the observation was called `cqrs.bus.dispatch`. Dashboards, alerts or trace queries that look for spans (or for the timer Micrometer derives from the observation) under that name must use `cqrs.bus.handle`, or set `cqrs.tracing.observation-name` back to a name of their choice. The `MicrometerBusObservability` timer keeps `cqrs.bus.dispatch`.

Keep the name apart from `cqrs.bus.dispatch`, the timer of `MicrometerBusObservability`: Micrometer also derives a timer from the observation, and Prometheus rejects two meters with the same name and different tags.

The span is a child of the active span when the dispatch starts, so an HTTP request → command → event handler chain stitches into a single trace.

### Auto-registration

The middleware is registered automatically when an `ObservationRegistry` bean is present in the context (Spring Boot Actuator provides one). It runs with `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` — right after `ContextPropagationMiddleware`, so the span sees the correlation ID and any context-derived attributes.

### Disabling

```yaml
cqrs:
  tracing:
    enabled: false
```

### Cross-transport propagation

The W3C `traceparent` header is propagated automatically by Spring AMQP / Spring Kafka's own observation instrumentation when you enable it on the templates:

```yaml
spring:
  rabbitmq:
    template:
      observation-enabled: true
  kafka:
    template:
      observation-enabled: true
```

`TracingMiddleware` deliberately does **not** mirror trace headers into `MessageContext` — doing so would re-prefix `traceparent` under `cqrs.context.` and break W3C interop with downstream services that don't use this library. Use the transport-native observation hook instead.

### Customizing the observation name

```yaml
cqrs:
  tracing:
    observation-name: my-service.cqrs.dispatch
```

## Examples

### LoggingMiddleware

Logs message processing with duration:

```java
@Component
@Order(1)
public class LoggingMiddleware implements BusMiddleware {

    private static final Logger log = LoggerFactory.getLogger(LoggingMiddleware.class);

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
        String messageName = message.getClass().getSimpleName();
        log.info("Processing message: {}", messageName);

        long start = System.currentTimeMillis();
        try {
            Object result = chain.proceed(message);
            long duration = System.currentTimeMillis() - start;
            log.info("Processed message: {} in {}ms", messageName, duration);
            return result;
        } catch (Exception e) {
            log.error("Failed to process message: {}", messageName, e);
            throw e;
        }
    }
}
```

### AuthorizationMiddleware

Checks authorization before allowing the message through:

```java
@Component
@Order(2)
public class AuthorizationMiddleware implements BusMiddleware {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationMiddleware.class);

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
        String messageName = message.getClass().getSimpleName();
        log.info("Authorizing message: {}", messageName);
        // Add your authorization logic here
        return chain.proceed(message);
    }
}
```

### TransactionalMiddleware

Wraps handler execution in transaction-like semantics:

```java
@Component
@Order(3)
public class TransactionalMiddleware implements BusMiddleware {

    private static final Logger log = LoggerFactory.getLogger(TransactionalMiddleware.class);

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
        String messageName = message.getClass().getSimpleName();
        log.info("Starting transaction for: {}", messageName);

        try {
            Object result = chain.proceed(message);
            log.info("Committing transaction for: {}", messageName);
            return result;
        } catch (Exception e) {
            log.error("Rolling back transaction for: {}", messageName, e);
            throw e;
        }
    }
}
```

See the [example-middleware](../examples/example-middleware) application for a running demonstration.
