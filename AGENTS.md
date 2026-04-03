# AGENTS.md — Spring Boot CQRS

> Guidelines for agentic coding agents working in this repository.

## Project Overview

A production-grade, GraalVM-compatible CQRS library for Spring Boot 3 and Spring Boot 4. Multi-module Gradle project with Java 21.

**Modules:**
- `spring-boot-cqrs-core` — Bus interfaces, base types, annotations, middleware, serialization SPI
- `spring-boot-cqrs-boot3-starter` — Spring Boot 3.5.x auto-configuration
- `spring-boot-cqrs-boot4-starter` — Spring Boot 4.0.x auto-configuration
- `spring-boot-cqrs-rabbitmq` — Distributed messaging adapter

## Build / Test Commands

### Essential Commands

```bash
# Run ALL tests with 100% JaCoCo coverage verification
./gradlew quality

# Run all tests (without coverage verification)
./gradlew test

# Run tests for a specific module
./gradlew :spring-boot-cqrs-core:test

# Run a SINGLE test class
./gradlew :spring-boot-cqrs-core:test --tests "CommandTest"

# Run a SINGLE test method
./gradlew :spring-boot-cqrs-core:test --tests "CommandTest.commandIdIsGenerated"

# Auto-format code (run BEFORE committing)
./gradlew spotlessApply

# Run Spotless check only (CI-style)
./gradlew spotlessCheck

# Run JaCoCo coverage verification only
./gradlew coverage

# Full check (tests + coverage + spotless)
./gradlew check
```

### Gradle Properties

- JVM args: `-Xmx2048m`
- Parallel builds, caching, and configuration cache are enabled
- Group: `com.borjaglez`

## Code Style

### Formatting

- **Google Java Format** — enforced by Spotless. Run `./gradlew spotlessApply` before committing
- **2-space indentation** (Google Java Format default)
- **No trailing whitespace**, files must end with newline
- **UTF-8 encoding** for all source files

### Import Order

Strictly enforced by Spotless in this order:
```
java.*
javax.*
jakarta.*
org.*
com.*
io.*
```
Unused imports are automatically removed.

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Classes | PascalCase | `SpringCommandBus`, `CommandValidationInterceptor` |
| Methods | camelCase | `dispatchAndReceive`, `handle` |
| Fields | camelCase | `commandId`, `registry` |
| Constants | UPPER_SNAKE_CASE | `BOM_COORDINATES` |
| Test classes | `ClassNameTest` | `CommandTest`, `SpringCommandBusTest` |
| Test methods | camelCase (descriptive) | `commandIdIsGenerated`, `dispatchCallsRegistry` |
| Test fixtures | `Test*` prefix | `TestCommand`, `TestEventHandler` |

### Lombok Usage

- Lombok is applied to ALL modules via `io.freefair.lombok` plugin
- Use `@Getter` on command/event/query classes for field accessors
- Do NOT use `@Data` — prefer explicit annotations
- Constructor injection is preferred; avoid `@Autowired` on fields

### Error Handling

- Wrap checked exceptions in domain-specific runtime exceptions (e.g., `CommandHandlerExecutionException`)
- Re-throw `RuntimeException` subclasses directly without wrapping
- Use custom exception types per domain: `CommandNotRegisteredException`, `QueryAlreadyRegisteredException`, etc.

### Annotations

- `@CommandHandler`, `@EventHandler`, `@QueryHandler` — mark handler classes
- `@HandleCommand`, `@HandleEvent`, `@HandleQuery` — mark handler methods
- `@CqrsMessage(service, module, name)` — annotate message types
- `@Order(n)` — control middleware execution order
- `@Component` — Spring bean registration (no XML config)

## Testing Guidelines

### Framework Stack

- **JUnit 5** (Jupiter) with `@ExtendWith(MockitoExtension.class)`
- **AssertJ** for fluent assertions: `assertThat(actual).isEqualTo(expected)`
- **Mockito** for mocking: `mock()`, `when()`, `verify()`
- **Testcontainers** for integration tests (RabbitMQ module)
- **Awaitility** for async/wait scenarios
- **Reactor Test** for reactive stream testing

### Test Structure

```java
@ExtendWith(MockitoExtension.class)
class ClassNameTest {

  @Test
  void descriptiveMethodName() {
    // Arrange
    Dependency dep = mock(Dependency.class);
    when(dep.method()).thenReturn("value");

    // Act
    ClassName instance = new ClassName(dep);
    String result = instance.method();

    // Assert
    assertThat(result).isEqualTo("value");
    verify(dep).method();
  }
}
```

### Test Conventions

- Tests live in `src/test/java` mirroring the main package structure
- Test fixtures (dummy commands, events, handlers) go in `com.borjaglez.cqrs.fixtures`
- No `@SpringBootTest` unless testing auto-configuration; prefer unit tests with mocks
- Integration tests using Testcontainers live in an `integration` subpackage
- **100% JaCoCo coverage** is enforced on both instructions AND branches — every test must be comprehensive

### Running Tests

```bash
# Single test method (most common during development)
./gradlew :spring-boot-cqrs-core:test --tests "SpringCommandBusTest.dispatchCallsRegistry"

# All tests in a class
./gradlew :spring-boot-cqrs-core:test --tests "SpringCommandBusTest"

# All tests in a module
./gradlew :spring-boot-cqrs-rabbitmq:test
```

## Architecture Notes

- **Core module is Jackson-free at runtime** — each starter brings its own Jackson version
- **Middleware pipeline** intercepts all bus dispatches via `BusMiddleware` interface
- **Handler discovery** is annotation-driven via `BeanPostProcessorHandlerDiscoverer`
- **GraalVM native support** via `CqrsRuntimeHintsRegistrar` and `CqrsBeanRegistrationAotProcessor`
- RabbitMQ event bus **falls back to local Spring event bus** if AMQP is unavailable

## Dependency Management

- Version catalog defined in `gradle/libs.versions.toml` (access as `libs.xxx`)
- Spring Boot BOM is imported automatically via convention plugins
- Use `compileOnly` for optional dependencies (validation, micrometer, jackson)
- Use `testImplementation` to enable optional deps during testing
- `annotationProcessor` for Spring configuration processors

## CI/CD

- GitHub Actions: CI runs on PR + push to `main` against Java 21 and 22
- `./gradlew quality` is the gate — must pass with 100% coverage
- JaCoCo reports and test results uploaded as artifacts (14-day retention)
