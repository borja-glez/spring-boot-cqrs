# Middleware Documentation

The middleware pipeline intercepts every message dispatched through any of the three buses (Command, Event, Query). Middleware can inspect, modify, or short-circuit messages before they reach handlers.

## Table of Contents

- [BusMiddleware Interface](#busmiddleware-interface)
- [MiddlewareChain](#middlewarechain)
- [Creating Custom Middleware](#creating-custom-middleware)
- [Ordering with @Order](#ordering-with-order)
- [Built-in Middleware](#built-in-middleware)
- [Message Context & Correlation ID](#message-context--correlation-id)
- [Examples](#examples)

## BusMiddleware Interface

```java
@FunctionalInterface
public interface BusMiddleware {
    Object process(Object message, MiddlewareChain chain) throws Exception;
}
```

The `message` parameter is the `Command`, `Event`, or `Query` being dispatched. Call `chain.proceed(message)` to pass the message to the next middleware in the chain (or to the terminal handler if this is the last middleware). Return the result from `chain.proceed()` to propagate the handler's return value.

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

The built-in `CommandValidationInterceptor` and `MicrometerBusObservability` are also ordered as middleware beans and participate in the same pipeline.

## Built-in Middleware

### CommandValidationInterceptor

**Package:** `com.borjaglez.cqrs.validation`  
**Auto-configured:** Yes, when JSR-380 (`jakarta.validation`) is on the classpath  
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

Records a `cqrs.bus.dispatch` timer for every message dispatched through any bus. Tags:

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
**Auto-configured:** Yes, when SLF4J (`org.slf4j.MDC`) is on the classpath
**Property:** `cqrs.context.enabled` (default: `true`)
**Order:** `Ordered.HIGHEST_PRECEDENCE` — runs before validation, observability, and any user middleware.

Propagates an immutable `MessageContext` through every bus dispatch. The context carries business-level metadata (correlation ID, tenant ID, user ID, any key/value pair) that handlers and other middleware can read via `MessageContext.current()`. Nothing needs to be threaded through method parameters.

On entry the middleware:

1. Reads the current `MessageContext` from a `ThreadLocal`.
2. If no `correlationId` is present and `cqrs.context.auto-correlation-id=true` (default), generates a UUID and adds it.
3. Mirrors every configured key (see `cqrs.context.mdc-keys`) into SLF4J MDC so downstream logs carry them automatically.
4. Proceeds through the chain.
5. Restores the previous MDC state and context on exit (even if the handler throws).

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

Both the RabbitMQ and Kafka adapters serialize every context entry into message headers, prefixed by `cqrs.context.header-prefix` (default: `cqrs.context.`). The consuming side rehydrates the context before the middleware chain runs, so the same `correlationId` flows across services without any application code.

| Transport | Header key on the wire |
|---|---|
| RabbitMQ | `cqrs.context.correlationId`, `cqrs.context.tenantId`, … |
| Kafka    | `cqrs.context.correlationId`, `cqrs.context.tenantId`, … |

### Logging with MDC

With the default configuration, SLF4J MDC always contains `correlationId` inside the handler execution. A Logback pattern such as:

```xml
<pattern>%d{ISO8601} [%X{correlationId:-}] %-5level %logger{36} - %msg%n</pattern>
```

automatically annotates every log line. Mirror additional keys by setting `cqrs.context.mdc-keys=correlationId,tenantId,userId`.

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
