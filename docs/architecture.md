# Architecture

The project is split into a framework-free API, a core with the Spring implementations, and Spring Boot adapter starters.

- `spring-boot-cqrs-api` contains the public API: the messages (`Command`, `Event`, `Query`, `AggregateRoot`, `KeyedMessage`), the bus interfaces, `ResultType`, and the annotations (`@CommandHandler`, `@HandleCommand` and their query and event counterparts, `@CqrsMessage`, `@Idempotent`). It has no dependencies, so domain and application code can use it without Spring.
- `spring-boot-cqrs-core` depends on the API and contains the Spring bus implementations, middleware pipeline, registries, handler discovery, serialization SPI, and AOT support.
- `spring-boot-cqrs-boot3-starter` and `spring-boot-cqrs-boot4-starter` provide Spring Boot auto-configuration, handler discovery, and Jackson serialization for their respective Spring Boot generations.
- `spring-boot-cqrs-rabbitmq` provides distributed bus implementations over RabbitMQ with retry and dead-letter queue support.

All library code lives under the `com.borjaglez.cqrs` package hierarchy. The API and the core share packages (`com.borjaglez.cqrs.command`, `com.borjaglez.cqrs.event`, ...), which works on the classpath but not as named JPMS modules, so neither declares an `Automatic-Module-Name`.

Because the API has no dependencies, the domain and use cases of an application can depend on it alone while the Spring buses, the starters and the adapters stay in the infrastructure. [Hexagonal Architecture](hexagonal.md) shows a complete layout.

## Dispatch pipeline

<p align="center"><img src="assets/diagrams/dispatch-pipeline.svg" alt="Where middleware runs. A local dispatch goes from the caller through SpringCommandBus, SpringQueryBus or springEventBus and the LOCAL middleware to the handler. A remote dispatch runs the OUTBOUND middleware in the sending process and publishes; the consumer of the receiving process rehydrates the message context from the headers and runs the INBOUND middleware before the handler. A table lists the built-in middleware in order with their phases: ContextPropagationMiddleware (HIGHEST_PRECEDENCE, all phases), TracingMiddleware (HIGHEST_PRECEDENCE + 10), RetryMiddleware (LOWEST_PRECEDENCE - 100), TransactionMiddleware (LOWEST_PRECEDENCE - 50), CommandValidationInterceptor (no @Order, all phases), MicrometerBusObservability (no @Order) and your own middleware; the chain ends in the registry, which calls the handler through IdempotentInvoker when it is @Idempotent." width="100%"></p>

Every bus receives all the `BusMiddleware` beans, in `@Order` order, and runs those whose `phases()` contain its `DispatchPhase`: `LOCAL` in the in-process buses, `OUTBOUND` on the sending side of the RabbitMQ and Kafka buses before the message is published, and `INBOUND` in their consumers before the handler. At the end of a `LOCAL` or `INBOUND` chain the registry invokes the handler, through `IdempotentInvoker` when the handler is `@Idempotent`. The opt-in `TransactionMiddleware` is ordered inside the opt-in `RetryMiddleware`, so every retry attempt runs in its own transaction. See [Middleware](middleware.md#middleware-on-remote-buses).

## Design goals

- The API has no dependencies at all; the core is framework-agnostic -- no Spring Boot dependencies at runtime.
- Each starter brings the correct Jackson version for its Spring Boot generation.
- Middleware pipeline intercepts all bus dispatches for cross-cutting concerns.
- Handler discovery is annotation-driven via `BeanPostProcessor`.
- GraalVM native image support with automatic AOT hint registration.
- Broker adapters report publish failures to the caller instead of falling back to local buses.

## Quality

- Spotless (Google Java Format) enforces consistent code style.
- Lombok reduces boilerplate.
- Dependency management is aligned with the Spring Boot BOM.
- 100% JaCoCo coverage is enforced with no exclusions (instructions + branches).
