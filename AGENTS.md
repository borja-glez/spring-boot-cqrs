# AGENTS.md — Spring Boot CQRS

> Guidelines for agentic coding agents working in this repository.

## Project Overview

A production-grade, GraalVM-compatible CQRS library for Spring Boot 3 and Spring Boot 4. Multi-module Gradle project with Java 21.

**Modules:**
- `spring-boot-cqrs-core` — Bus interfaces, base types, annotations, middleware, serialization SPI
- `spring-boot-cqrs-boot3-starter` — Spring Boot 3.5.x auto-configuration
- `spring-boot-cqrs-boot4-starter` — Spring Boot 4.0.x auto-configuration
- `spring-boot-cqrs-rabbitmq` — RabbitMQ distributed messaging adapter
- `spring-boot-cqrs-kafka` — Kafka distributed messaging adapter
- `spring-boot-cqrs-test` — Test support: spy and in-memory buses, AssertJ assertions, `@CqrsTest`

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
- Group: `com.borjaglez.cqrs`

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
- The RabbitMQ and Kafka event buses **throw** when an event cannot be sent (`AmqpException`, or the `KafkaMessagePublisher` exception); there is no fallback to the local Spring event bus. Reliable publication goes through an outbox

## Dependency Management

- Version catalog defined in `gradle/libs.versions.toml` (access as `libs.xxx`)
- Spring Boot BOM is imported automatically via convention plugins
- Use `compileOnly` for optional dependencies (validation, micrometer, jackson)
- Use `testImplementation` to enable optional deps during testing
- `annotationProcessor` for Spring configuration processors

## CI/CD

- GitHub Actions: CI runs on PRs; code is compiled for Java 21 and the tests run on JDK 21 and 25 (`-PtestJavaVersion=25` locally)
- `./gradlew quality` is the gate — must pass with 100% coverage
- JaCoCo reports and test results uploaded as artifacts (14-day retention)

## Working on an issue

Issues are written as work orders: Context, Reproduction, Expected behaviour, Where to look, Proposed approach, Acceptance criteria, Compatibility, Dependencies, Out of scope. Follow them; they are the spec.

1. **Check the labels first.** `agent-ready` means implement it. `needs-design` means do **not** implement unless the issue body has a `### Decision` section recorded by the maintainer; otherwise stop and report the open questions.
2. **Check `Dependencies`.** If an issue listed there is still open, stop and report it instead of re-implementing it.
3. **Branch** from the latest `main`: `fix/<issue-number>-<short-slug>` for bugs, `feat/<issue-number>-<short-slug>` for features, `docs/<issue-number>-<short-slug>` for documentation.
4. **Regression test first.** Write the test named in the acceptance criteria, see it fail for the reason described, then fix. Keep the test in the same change.
5. **Both starters.** When auto-configuration or starter code changes, apply and test it in the Boot 3 and the Boot 4 starter.
6. **Verify before pushing:** `./gradlew spotlessApplyAll`, then `./gradlew quality` (tests, 100% line and branch coverage, Spotless). If a starter or an example changed, also run `./gradlew verifyBoot3Compatibility verifyBoot4Compatibility`.
7. **Commits:** Conventional Commits with a scope (`fix(http): ...`, `feat(kafka): ...`). The subject says what changes; the body, when needed, says why and how. A breaking change uses `!` in the subject and a `BREAKING CHANGE:` footer.
8. **Never** add `Co-Authored-By` lines, "Generated with ..." footers, session links, or any mention of Claude, AI, a model or a session in commits, pull request titles or pull request descriptions.
9. **Pull request:** title equal to the commit subject; body from `.github/pull_request_template.md` with `Closes #<issue>` and a short explanation of how it was solved; same labels as the issue. Do not merge it: the maintainer squash-merges once CI is green.
10. **Stay in scope.** Do not touch `CHANGELOG.md` (generated from commits), the version in `gradle.properties`, the workflows, or code outside the issue's `Where to look` unless the acceptance criteria need it.
11. **When blocked** (ambiguous behaviour, an acceptance criterion that cannot be met, a failing test you cannot explain), open the pull request as a draft and describe the blocker instead of guessing.
