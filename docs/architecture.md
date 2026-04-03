# Architecture

The project is split into a pure core with bus interfaces and Spring Boot adapter starters.

- `spring-boot-cqrs-core` contains all bus interfaces, base types, annotations, middleware pipeline, registries, serialization SPI, and AOT support.
- `spring-boot-cqrs-boot3-starter` and `spring-boot-cqrs-boot4-starter` provide Spring Boot auto-configuration, handler discovery, and Jackson serialization for their respective Spring Boot generations.
- `spring-boot-cqrs-rabbitmq` provides distributed bus implementations over RabbitMQ with retry and dead-letter queue support.

All library code lives under the `com.borjaglez.cqrs` package hierarchy.

## Design goals

- Core is framework-agnostic -- no Spring Boot dependencies at runtime.
- Each starter brings the correct Jackson version for its Spring Boot generation.
- Middleware pipeline intercepts all bus dispatches for cross-cutting concerns.
- Handler discovery is annotation-driven via `BeanPostProcessor`.
- GraalVM native image support with automatic AOT hint registration.
- RabbitMQ adapters fall back to local buses when unavailable.

## Quality

- Spotless (Google Java Format) enforces consistent code style.
- Lombok reduces boilerplate.
- Dependency management is aligned with the Spring Boot BOM.
- 100% JaCoCo coverage is enforced with no exclusions (instructions + branches).
