# Extensibility

The library is designed around extension contracts in the `com.borjaglez.cqrs` package:

- `BusMiddleware`: add cross-cutting concerns to any bus dispatch (logging, validation, auth, transactions).
- `MessageNamingStrategy`: customize how message names are resolved from `@CqrsMessage` annotations.
- `MessageSerializer`: plug in custom serialization (Jackson is the default; any SPI implementation works).
- `RabbitMqNamingStrategy`: customize RabbitMQ exchange and queue naming.

Example custom middleware use cases:

- JSR-380 validation (already built-in)
- Micrometer observability (already built-in)
- Authorization checks before handler execution
- Transaction boundary management
- Request enrichment or transformation
