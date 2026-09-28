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

### KeyedMessage

```java
package com.borjaglez.cqrs;

public interface KeyedMessage {
    String messageKey();
}
```

Opt-in interface for commands and events that belong to an entity (usually an aggregate) and must keep their order relative to the other messages of that entity. Transports that order by key use it: the Kafka adapter sends `messageKey()` as the record key, so all the messages with the same key, of any type, go to the same partition and are consumed in publication order, and also as the `cqrs.message.key` header (see [Partition Keys](kafka-adapter.md#partition-keys)).

```java
public class OrderCancelled extends Event implements KeyedMessage {
    private UUID orderId;

    @Override
    public String messageKey() {
        return orderId.toString();
    }
}
```

- A `null` or blank key means "no key": the transport keeps its configured behaviour.
- Queries have no ordering need; a query implementing the interface is sent as if it did not.
- `messageKey()` is not a bean getter, so Jackson does not add it to the payload. A record with a `messageKey` component implements it directly.
- It is read through the interface, without reflection, so it needs no GraalVM hints.

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

Handler methods must be instance methods: `static` handler methods are rejected at startup. When the handler bean is proxied (for example because of `@Transactional`, `@Async` or `@Secured`), handler methods must also be `public` and non-`final`, so that the call goes through the proxy and its advice; with a JDK dynamic proxy (`proxyTargetClass=false`) the handler method must also be declared on one of the proxied interfaces. Otherwise the application fails at startup with an error naming the bean and the method.

### Conditional event handlers

`@HandleEvent` accepts an optional SpEL `condition`. The handler only runs for events for which the expression evaluates to `true`; other handlers of the same event are not affected.

```java
@EventHandler
class OrderNotifications {

  @HandleEvent(condition = "newStatus == 'CONFIRMED'")          // property of the event (root object)
  void onConfirmed(OrderStatusChanged event) { ... }

  @HandleEvent(condition = "#event.priority > 5")               // the event as the #event variable
  void onUrgent(OrderStatusChanged event) { ... }

  @HandleEvent(condition = "@featureFlags.enabled('notify')")   // reference to a bean
  void onNotify(OrderStatusChanged event) { ... }
}
```

- An empty condition (the default) means the handler always runs; no expression is evaluated.
- The expression is parsed once, when the handler is registered. A malformed expression fails startup with an `IllegalStateException` naming the handler method, its bean and the expression.
- On each dispatch the expression is evaluated with a `StandardEvaluationContext`: the root object is the event, the `#event` variable is the event too, and `@beanName` resolves beans of the application context.
- `true` invokes the handler and `false` skips it silently. A `null` or non-boolean result, or an exception thrown while evaluating the expression, raises an `EventHandlerExecutionException` naming the handler and the expression.
- Conditions apply wherever `EventHandlerRegistry.handle` or `handleRemote` dispatches: `SpringEventBus`, `TransactionalEventBus` and the RabbitMQ and Kafka event consumers. They are evaluated after the event has reached the application: there is no broker-side filtering, so the event is still delivered (and deserialized) before the condition discards it.
- Conditions are only available on event handlers; commands and queries have exactly one handler each.
- In a native image, a bean referenced from a condition needs the methods the expression calls to be reachable by reflection (see [graalvm-native.md](graalvm-native.md#known-limitations)).

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

The annotation is optional. The in-process buses dispatch by Java type and never read the name, so messages handled inside one application need no annotation. Annotate the messages that cross a service boundary (commands and queries that other services send, events that other services consume): the name is the RabbitMQ routing key, and RabbitMQ exposes only annotated messages by default ([Exposed and local messages](rabbitmq-adapter.md#exposed-and-local-messages)); on Kafka it is the `cqrs.message.name` header and the default partition key ([Partition Keys](kafka-adapter.md#partition-keys)). A service-qualified name does not collide with a message of another service and survives renaming or moving the class.

## Registries

### CommandHandlerRegistry

Stores the mapping from command class to handler. Each command class can have at most one handler. Throws `CommandAlreadyRegisteredException` if a duplicate registration is attempted.

```java
public class CommandHandlerRegistry {
    record HandlerInfo(Object bean, MethodHandle handle, String messageName, boolean requiresValidation, boolean remote) {}

    void register(Class<?> commandClass, Object bean, Method method, String messageName, boolean requiresValidation);
    void register(Class<?> commandClass, Object bean, Method method, String messageName, boolean requiresValidation, boolean remote);
    Object handle(Command command);
    Optional<HandlerInfo> getHandlerInfo(Class<?> commandClass);
    Set<Class<?>> getRegisteredCommands();
}
```

### EventHandlerRegistry

Stores the mapping from event class to handler(s). An event can have **multiple** handlers. All registered handlers are invoked when an event is published.

```java
public class EventHandlerRegistry {
    record HandlerInfo(Object bean, MethodHandle handle, String messageName,
                       EventHandlerCondition condition, boolean remote) {}

    void register(Class<?> eventClass, Object bean, Method method, String messageName);
    void register(Class<?> eventClass, Object bean, Method method, String messageName, boolean remote);
    void register(Class<?> eventClass, Object bean, Method method, String messageName,
                  Expression condition, BeanResolver beanResolver);
    void register(Class<?> eventClass, Object bean, Method method, String messageName,
                  Expression condition, BeanResolver beanResolver, boolean remote);
    void handle(Event event);       // every handler
    void handleRemote(Event event); // only handlers not marked remote = false
    Set<Class<?>> getRegisteredEvents();
}
```

Events with no registered handlers are silently ignored. A handler registered with a `condition` (see [Conditional event handlers](#conditional-event-handlers)) is skipped when the condition evaluates to `false`; `HandlerInfo.condition()` is `null` for handlers without one, and the three-argument `HandlerInfo` constructor still creates such a handler.

### QueryHandlerRegistry

Stores the mapping from query class to handler. Each query class can have at most one handler. Throws `QueryAlreadyRegisteredException` if a duplicate registration is attempted.

```java
public class QueryHandlerRegistry {
    record HandlerInfo(Object bean, MethodHandle handle, String messageName, boolean remote) {}

    void register(Class<?> queryClass, Object bean, Method method, String messageName);
    void register(Class<?> queryClass, Object bean, Method method, String messageName, boolean remote);
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
    void dispatchAndWait(Command command);
    <R> R dispatchAndReceive(Command command);
    <R> R dispatchAndReceive(Command command, ParameterizedTypeReference<R> responseType);
}
```

- `dispatch` -- fire-and-forget; the handler return value is discarded.
- `dispatchAndWait` -- dispatches and waits for the handler to finish; the return value is discarded.
- `dispatchAndReceive` -- dispatches and returns the handler's return value.
- `dispatchAndReceive(command, responseType)` -- the same, for a generic result sent over a remote bus (see [Generic results on remote buses](#generic-results-on-remote-buses)).

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
    <R> R ask(Query query, ParameterizedTypeReference<R> responseType);
}
```

Dispatches a query and returns the handler's result. The `responseType` overload is for a generic result sent over a remote bus.

### Generic results on remote buses

The RabbitMQ and Kafka buses send a result back with its runtime class only, so the requester loses the type arguments of a generic result: without a type reference a `List<OrderDto>` comes back as a list of `LinkedHashMap`. Results whose class describes them fully (records, POJOs, `String`, boxed primitives) are not affected. Pass a `ParameterizedTypeReference` to keep the element types:

```java
List<OrderDto> orders =
    queryBus.ask(new ListOrders(), new ParameterizedTypeReference<List<OrderDto>>() {});
```

The local buses (`SpringCommandBus`, `SpringQueryBus`) return the handler's object as it is; the default overloads ignore the type reference. See the [RabbitMQ](rabbitmq-adapter.md#generic-results) and [Kafka](kafka-adapter.md#generic-results) adapter guides.

## Spring Implementations

### SpringCommandBus

In-process implementation that resolves handlers from `CommandHandlerRegistry` and runs the middleware pipeline before invoking the handler.

### SpringEventBus

In-process implementation that resolves handlers from `EventHandlerRegistry` and runs the middleware pipeline before invoking handlers. All handlers for a given event type are called sequentially.

### SpringQueryBus

In-process implementation that resolves handlers from `QueryHandlerRegistry` and runs the middleware pipeline before invoking the handler.

All three implementations accept a `List<BusMiddleware>` and run, in order and before the terminal handler invocation, the middlewares that declare `DispatchPhase.LOCAL` (every middleware by default).

## Handler Discovery

### BeanPostProcessorHandlerDiscoverer

Implements Spring's `BeanPostProcessor` to scan beans at startup. For each bean:

1. If the class is annotated with `@CommandHandler`, it scans for methods annotated with `@HandleCommand` and registers them in `CommandHandlerRegistry`.
2. If the class is annotated with `@EventHandler`, it scans for methods annotated with `@HandleEvent` and registers them in `EventHandlerRegistry`.
3. If the class is annotated with `@QueryHandler`, it scans for methods annotated with `@HandleQuery` and registers them in `QueryHandlerRegistry`.

Each handler method is validated:
- Must have exactly one parameter.
- The parameter must extend the corresponding base type (`Command`, `Event`, or `Query`).
- Must not be `static`.
- If the bean is an AOP proxy, must be `public` and non-`final`, and reachable through the proxy (declared on a proxied interface for JDK dynamic proxies). The proxy's invocable method is registered, so dispatch runs through the advice.

For command handlers, if the parameter is annotated with `@Valid` (JSR-380), the `requiresValidation` flag is set to `true` in the registry.

For event handlers, a non-empty `@HandleEvent(condition = ...)` is parsed with a shared `SpelExpressionParser` and registered with the handler; a malformed expression fails startup. The discoverer is `BeanFactoryAware`, so conditions can reference beans with `@beanName`.

Every handler is registered with a `remote` flag, `true` by default. `remote = false` on the handler class (`@CommandHandler`, `@EventHandler`, `@QueryHandler`) or on the handler method (`@HandleCommand`, `@HandleEvent`, `@HandleQuery`) registers it as local only: the local buses still dispatch to it, but broker adapters neither bind nor consume its message (see [Exposed and local messages](rabbitmq-adapter.md#exposed-and-local-messages)). The registries' `register` overloads without the flag register remote handlers.

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

- `BusMiddleware` -- functional interface for intercepting bus dispatches; `phases()` says where it runs
- `DispatchPhase` -- `LOCAL`, `OUTBOUND` (sending side of remote buses) and `INBOUND` (their consumers); `select(middlewares)` keeps the middlewares of a phase
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
| `EventHandlerExecutionException` | A checked exception occurs during event handler invocation, or a `@HandleEvent` condition fails to evaluate or does not return a boolean |
| `QueryAlreadyRegisteredException` | A second handler is registered for the same query class |
| `QueryNotRegisteredException` | No handler is found for a dispatched query |
| `QueryHandlerExecutionException` | A checked exception occurs during query handler invocation |

All exceptions extend `RuntimeException`. The execution exceptions wrap the original cause. Runtime exceptions thrown by handlers are re-thrown directly without wrapping.

## AOT Support

See [graalvm-native.md](graalvm-native.md) for full documentation.

- `CqrsRuntimeHintsRegistrar` -- registers all CQRS annotations for reflection.
- `CqrsBeanRegistrationAotProcessor` -- registers handler beans and their message parameter types for reflection during AOT processing.
