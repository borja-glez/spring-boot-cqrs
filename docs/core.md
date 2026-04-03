# Core Module Documentation

Module: `spring-boot-cqrs-core`  
Package: `com.borjaglez.cqrs`

The core module contains all the fundamental types, interfaces, annotations, and infrastructure for the CQRS pattern. It has no Spring Boot auto-configuration -- that responsibility belongs to the starter modules (boot3-starter and boot4-starter).

## Table of Contents

- [Base Types](#base-types)
- [Annotations](#annotations)
- [Registries](#registries)
- [Bus Interfaces](#bus-interfaces)
- [Spring Implementations](#spring-implementations)
- [Handler Discovery](#handler-discovery)
- [Message Naming](#message-naming)
- [Serialization SPI](#serialization-spi)
- [Middleware](#middleware)
- [Exception Hierarchy](#exception-hierarchy)
- [AOT Support](#aot-support)

## Base Types

### Command

```java
package com.borjaglez.cqrs.command;

public abstract class Command {
    private final String commandId;  // auto-generated UUID
}
```

Base class for all commands. Each instance receives a unique `commandId` on construction. Commands represent an intent to change state. Equality is based on `commandId`.

### Event

```java
package com.borjaglez.cqrs.event;

public abstract class Event {
    private final String eventId;       // auto-generated UUID
    private final Instant occurredOn;   // timestamp of creation
}
```

Base class for all events. Each instance receives a unique `eventId` and an `occurredOn` timestamp. Events represent facts that have happened. Equality is based on `eventId`.

### Query

```java
package com.borjaglez.cqrs.query;

public abstract class Query {
    private final String queryId;  // auto-generated UUID
}
```

Base class for all queries. Each instance receives a unique `queryId`. Queries represent a request for data. Equality is based on `queryId`.

### AggregateRoot

```java
package com.borjaglez.cqrs.event;

public abstract class AggregateRoot {
    protected void record(Event event);
    public List<Event> pullEvents();
}
```

Base class for domain aggregates that produce events. Call `record(event)` inside domain methods to stage events. Call `pullEvents()` to retrieve and clear all staged events -- typically done in a command handler after persisting state.

```java
public class Order extends AggregateRoot {
    public void confirm() {
        this.status = Status.CONFIRMED;
        record(new OrderConfirmedEvent(this.id));
    }
}

// In handler:
order.confirm();
repository.save(order);
eventBus.publish(order.pullEvents());
```

## Annotations

### Type-level annotations (class)

| Annotation | Target | Purpose |
|---|---|---|
| `@CommandHandler` | Class | Marks a Spring bean as a command handler. Also acts as `@Service`. |
| `@EventHandler` | Class | Marks a Spring bean as an event handler. Also acts as `@Service`. |
| `@QueryHandler` | Class | Marks a Spring bean as a query handler. Also acts as `@Service`. |
| `@CqrsMessage` | Class | Declares a structured message name for routing and RabbitMQ integration. |

### Method-level annotations

| Annotation | Target | Purpose |
|---|---|---|
| `@HandleCommand` | Method | Marks a method as the handler for a specific command type. |
| `@HandleEvent` | Method | Marks a method as the handler for a specific event type. |
| `@HandleQuery` | Method | Marks a method as the handler for a specific query type. |

Handler methods must accept **exactly one parameter** that extends the corresponding base type (`Command`, `Event`, or `Query`). The parameter type determines which message class is routed to the method.

### @CqrsMessage

```java
@CqrsMessage(service = "order-service", version = 1, module = "order", name = "create-order")
public class CreateOrderCommand extends Command { ... }
```

Attributes:

| Attribute | Required | Default | Description |
|---|---|---|---|
| `service` | Yes | -- | Service name |
| `version` | No | `1` | Message version |
| `module` | Yes | -- | Logical module within the service |
| `name` | Yes | -- | Message name |

The generated name follows the pattern: `{prefix}.{service}.{version}.{type}.{module}.{name}`

For example, with prefix `"app"`: `app.order-service.1.command.order.create-order`

If `@CqrsMessage` is not present, the class simple name is converted to kebab-case (e.g., `CreateOrderCommand` becomes `create-order-command`).

## Registries

### CommandHandlerRegistry

Stores the mapping from command class to handler. Each command class can have at most one handler. Throws `CommandAlreadyRegisteredException` if a duplicate registration is attempted.

```java
public class CommandHandlerRegistry {
    record HandlerInfo(Object bean, MethodHandle handle, String messageName, boolean requiresValidation) {}

    void register(Class<?> commandClass, Object bean, Method method, String messageName, boolean requiresValidation);
    Object handle(Command command);
    Optional<HandlerInfo> getHandlerInfo(Class<?> commandClass);
    Set<Class<?>> getRegisteredCommands();
}
```

### EventHandlerRegistry

Stores the mapping from event class to handler(s). An event can have **multiple** handlers. All registered handlers are invoked when an event is published.

```java
public class EventHandlerRegistry {
    record HandlerInfo(Object bean, MethodHandle handle, String messageName) {}

    void register(Class<?> eventClass, Object bean, Method method, String messageName);
    void handle(Event event);
    Set<Class<?>> getRegisteredEvents();
}
```

Events with no registered handlers are silently ignored.

### QueryHandlerRegistry

Stores the mapping from query class to handler. Each query class can have at most one handler. Throws `QueryAlreadyRegisteredException` if a duplicate registration is attempted.

```java
public class QueryHandlerRegistry {
    record HandlerInfo(Object bean, MethodHandle handle, String messageName) {}

    void register(Class<?> queryClass, Object bean, Method method, String messageName);
    Object handle(Query query);
    Set<Class<?>> getRegisteredQueries();
}
```

All registries use `MethodHandle` instead of `Method.invoke()` for improved performance. The `MethodHandleUtil` utility creates unreflected method handles at registration time.

## Bus Interfaces

### CommandBus

```java
public interface CommandBus {
    void dispatch(Command command);
    <R> R dispatchAndReceive(Command command);
}
```

- `dispatch` -- fire-and-forget; the handler return value is discarded.
- `dispatchAndReceive` -- dispatches and returns the handler's return value.

### EventBus

```java
public interface EventBus {
    void publish(Event event);
    void publish(List<Event> events);
}
```

Publishes one or more events. All registered handlers for each event type are invoked.

### QueryBus

```java
public interface QueryBus {
    <R> R ask(Query query);
}
```

Dispatches a query and returns the handler's result.

## Spring Implementations

### SpringCommandBus

In-process implementation that resolves handlers from `CommandHandlerRegistry` and runs the middleware pipeline before invoking the handler.

### SpringEventBus

In-process implementation that resolves handlers from `EventHandlerRegistry` and runs the middleware pipeline before invoking handlers. All handlers for a given event type are called sequentially.

### SpringQueryBus

In-process implementation that resolves handlers from `QueryHandlerRegistry` and runs the middleware pipeline before invoking the handler.

All three implementations accept a `List<BusMiddleware>` which is executed in order before the terminal handler invocation.

## Handler Discovery

### BeanPostProcessorHandlerDiscoverer

Implements Spring's `BeanPostProcessor` to scan beans at startup. For each bean:

1. If the class is annotated with `@CommandHandler`, it scans for methods annotated with `@HandleCommand` and registers them in `CommandHandlerRegistry`.
2. If the class is annotated with `@EventHandler`, it scans for methods annotated with `@HandleEvent` and registers them in `EventHandlerRegistry`.
3. If the class is annotated with `@QueryHandler`, it scans for methods annotated with `@HandleQuery` and registers them in `QueryHandlerRegistry`.

Each handler method is validated:
- Must have exactly one parameter.
- The parameter must extend the corresponding base type (`Command`, `Event`, or `Query`).

For command handlers, if the parameter is annotated with `@Valid` (JSR-380), the `requiresValidation` flag is set to `true` in the registry.

The discoverer uses `AopUtils.getTargetClass()` to handle proxied beans correctly.

## Message Naming

### MessageNamingStrategy

```java
public interface MessageNamingStrategy {
    String commandName(Class<?> commandClass);
    String eventName(Class<?> eventClass);
    String queryName(Class<?> queryClass);
}
```

### DefaultMessageNamingStrategy

The default implementation resolves names using two strategies:

1. **@CqrsMessage annotation** (preferred): Builds a structured name from the annotation attributes.
2. **Fallback**: Converts the class simple name to kebab-case.

The structured name format is: `{prefix}.{service}.{version}.{type}.{module}.{name}`

Example with prefix `"app"`:
- `@CqrsMessage(service="order", module="order", name="create")` on a Command produces: `app.order.1.command.order.create`

## Serialization SPI

### MessageSerializer

```java
public interface MessageSerializer {
    byte[] serialize(Object message);
    <T> T deserialize(byte[] data, Class<T> type);
}
```

### JacksonMessageSerializer

Default implementation backed by Jackson's `ObjectMapper`. The core module declares Jackson as a `compileOnly` dependency -- it does not bring Jackson at runtime. Each starter (boot3-starter or boot4-starter) provides the correct Jackson version and auto-configures `JacksonMessageSerializer` when Jackson is on the classpath.

## Middleware

See [middleware.md](middleware.md) for full documentation.

The core module defines:

- `BusMiddleware` -- functional interface for intercepting bus dispatches
- `MiddlewareChain` -- chain-of-responsibility interface
- `DefaultMiddlewareChain` -- ordered pipeline implementation
- `CommandValidationInterceptor` -- JSR-380 validation for commands
- `MicrometerBusObservability` -- Micrometer timer metrics
- `BusObservabilityInterceptor` -- marker interface for observability middleware

## Exception Hierarchy

| Exception | Thrown when |
|---|---|
| `CommandAlreadyRegisteredException` | A second handler is registered for the same command class |
| `CommandNotRegisteredException` | No handler is found for a dispatched command |
| `CommandHandlerExecutionException` | A checked exception occurs during command handler invocation |
| `EventHandlerExecutionException` | A checked exception occurs during event handler invocation |
| `QueryAlreadyRegisteredException` | A second handler is registered for the same query class |
| `QueryNotRegisteredException` | No handler is found for a dispatched query |
| `QueryHandlerExecutionException` | A checked exception occurs during query handler invocation |

All exceptions extend `RuntimeException`. The execution exceptions wrap the original cause. Runtime exceptions thrown by handlers are re-thrown directly without wrapping.

## AOT Support

See [graalvm-native.md](graalvm-native.md) for full documentation.

- `CqrsRuntimeHintsRegistrar` -- registers all CQRS annotations for reflection.
- `CqrsBeanRegistrationAotProcessor` -- registers handler beans and their message parameter types for reflection during AOT processing.
