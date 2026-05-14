# Testing

`spring-boot-cqrs-test` ships utilities for testing CQRS applications without
having to boot a full Spring application or mock the bus interfaces by hand.

## Table of Contents

- [Installation](#installation)
- [In-Memory Buses](#in-memory-buses)
- [Spy Buses](#spy-buses)
- [AssertJ Assertions](#assertj-assertions)
- [`@CqrsTest` Slice](#cqrstest-slice)
- [Middleware Test Harness](#middleware-test-harness)
- [When to use which](#when-to-use-which)

## Installation

**Gradle**

```kotlin
testImplementation("com.borjaglez.cqrs:spring-boot-cqrs-test:0.2.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.cqrs</groupId>
    <artifactId>spring-boot-cqrs-test</artifactId>
    <version>0.2.0</version>
    <scope>test</scope>
</dependency>
```

The module is Spring Boot version agnostic and works for both Boot 3 and Boot 4
consumers. AssertJ, JUnit, and `spring-boot-test` are required at test runtime
but declared as `compileOnly` on the artifact -- they always come transitively
with `spring-boot-starter-test`.

## In-Memory Buses

`InMemoryCommandBus`, `InMemoryEventBus`, and `InMemoryQueryBus` are standalone
implementations of the three bus interfaces. They register handlers
programmatically (via lambdas) and do not require a Spring context. Useful for
pure unit tests where Spring is overhead.

```java
@Test
void createsOrder() {
    InMemoryCommandBus bus = new InMemoryCommandBus()
        .register(CreateOrderCommand.class, cmd -> "order-123");

    String orderId = bus.dispatchAndReceive(new CreateOrderCommand("item"));

    assertThat(orderId).isEqualTo("order-123");
}
```

Event subscribers can be added with `subscribe(...)` and multiple handlers per
event type are supported:

```java
InMemoryEventBus events = new InMemoryEventBus()
    .subscribe(OrderCreatedEvent.class, e -> log.info("first listener"))
    .subscribe(OrderCreatedEvent.class, e -> notifyDownstream(e));

events.publish(new OrderCreatedEvent("order-123"));
```

Dispatching a command or asking a query with no registered handler throws
`NoHandlerRegisteredException`. The in-memory buses do not run middleware
pipelines -- they exist solely to invoke a single handler. Use `Handlers.invoke`
(below) when you need a middleware chain without a full bus, or use the spy
buses inside `@CqrsTest` to exercise middleware end to end.

## Spy Buses

`SpyCommandBus`, `SpyEventBus`, and `SpyQueryBus` decorate any existing bus and
record every dispatch / publication / ask for later assertion. They implement
`AssertProvider<...>` so `Assertions.assertThat(spy)` returns the right
fluent assert directly.

```java
SpyCommandBus commandBus = new SpyCommandBus(realBus);

commandBus.dispatch(new CreateOrderCommand("item-1"));

assertThat(commandBus).dispatched(CreateOrderCommand.class).once();
```

`recorded()` returns an immutable snapshot of the captured messages; `clear()`
resets the recording. Recording happens *before* delegation, so a dispatch that
the inner bus rejects with an exception still shows up in `recorded()` and can
be asserted.

## AssertJ Assertions

The fluent API on the bus asserts supports filtering by type, exact counts, and
content predicates. Each assertion is independently chainable:

```java
assertThat(commandBus)
    .dispatched(CreateOrderCommand.class)
    .times(2)
    .matching(c -> ((CreateOrderCommand) c).getItem().equals("widget"));

assertThat(eventBus).published(OrderCreatedEvent.class).never();

assertThat(queryBus).asked(GetOrderQuery.class).once();
```

`once()` is shorthand for `times(1)`, `never()` for `times(0)`. The optional
`matching(predicate)` asserts that *at least one* of the filtered messages
satisfies the predicate.

The static entry point `CqrsAssertions.assertThat(...)` is also available for
projects that prefer named imports over the `AssertProvider`-driven AssertJ
default.

## `@CqrsTest` Slice

`@CqrsTest` boots a minimal Spring context containing only:

- The three handler registries.
- A default `MessageNamingStrategy`.
- The `BeanPostProcessorHandlerDiscoverer` that scans annotated handlers.
- Spy-wrapped Spring buses (registered as `@Primary` so injecting `CommandBus`,
  `EventBus`, or `QueryBus` returns the spy).

Use `@Import` to register the handlers you want to exercise; the discoverer
picks them up automatically.

```java
@CqrsTest
@Import({CreateOrderCommandHandler.class, OrderCreatedEventHandler.class})
class OrderFlowTest {

    @Autowired SpyCommandBus commandBus;
    @Autowired SpyEventBus eventBus;

    @Test
    void creatingOrderPublishesEvent() {
        commandBus.dispatchAndReceive(new CreateOrderCommand("widget"));

        assertThat(eventBus)
            .published(OrderCreatedEvent.class)
            .once()
            .matching(e -> ((OrderCreatedEvent) e).getOrderId() != null);
    }
}
```

Because spy buses wrap the real `SpringCommandBus`/`SpringEventBus`/
`SpringQueryBus`, the entire middleware pipeline runs as in production --
including any `BusMiddleware` beans contributed by `@Import` or
`@TestConfiguration` classes.

## Middleware Test Harness

`Handlers.invoke(message, handler, middlewares...)` builds a one-shot middleware
chain around a handler function. Useful for testing custom middleware in
isolation without booting a bus:

```java
@Test
void validationMiddlewareRejectsInvalidCommand() {
    BusMiddleware validation = new CommandValidationInterceptor(validator);

    assertThatThrownBy(() ->
        Handlers.invoke(
            new CreateOrderCommand(""),
            cmd -> "ignored",
            validation))
        .isInstanceOf(ConstraintViolationException.class);
}
```

The variadic `middlewares` argument accepts zero or more middlewares; if `null`
is passed, the chain is treated as empty.

## When to use which

| Need | Use |
|---|---|
| Unit test a single handler in isolation, no Spring | `InMemoryXxxBus` + programmatic `register(...)` |
| Unit test a middleware in isolation, no bus | `Handlers.invoke(...)` |
| Verify that code under test dispatched/published certain messages | `SpyXxxBus` wrapping a fake or real bus |
| End-to-end test of a handler with the full registry + middleware | `@CqrsTest` + `@Import(MyHandler.class)` |
