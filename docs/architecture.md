# Architecture

The project is split into a framework-free API, a core with the Spring implementations, and Spring Boot adapter starters.

- `spring-boot-cqrs-api` contains the public API: the messages (`Command`, `Event`, `Query`, `AggregateRoot`, `KeyedMessage`), the bus interfaces, `ResultType`, and the annotations (`@CommandHandler`, `@HandleCommand` and their query and event counterparts, `@CqrsMessage`, `@Idempotent`). It has no dependencies, so domain and application code can use it without Spring.
- `spring-boot-cqrs-core` depends on the API and contains the Spring bus implementations, middleware pipeline, registries, handler discovery, serialization SPI, and AOT support.
- `spring-boot-cqrs-boot3-starter` and `spring-boot-cqrs-boot4-starter` provide Spring Boot auto-configuration, handler discovery, and Jackson serialization for their respective Spring Boot generations.
- `spring-boot-cqrs-rabbitmq` provides distributed bus implementations over RabbitMQ with retry and dead-letter queue support.

All library code lives under the `com.borjaglez.cqrs` package hierarchy. The API and the core share packages (`com.borjaglez.cqrs.command`, `com.borjaglez.cqrs.event`, ...), which works on the classpath but not as named JPMS modules, so neither declares an `Automatic-Module-Name`.

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
