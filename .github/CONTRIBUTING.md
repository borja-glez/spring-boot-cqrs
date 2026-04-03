# Contributing to spring-boot-cqrs

Thanks for your interest in contributing! Here is how to get started.

## Getting Started

1. **Fork** the repository and clone your fork:

   ```bash
   git clone https://github.com/<your-username>/spring-boot-cqrs.git
   cd spring-boot-cqrs
   ```

2. **Create a feature branch** from `main`:

   ```bash
   git checkout -b feature/my-change
   ```

3. **Make your changes** and verify everything works:

   ```bash
   ./gradlew quality
   ```

   This runs all tests and enforces 100% JaCoCo coverage (instructions + branches) across all library modules.

4. **Format your code** before committing:

   ```bash
   ./gradlew spotlessApply
   ```

5. **Commit** using [conventional commits](https://www.conventionalcommits.org/):

   ```
   feat: add retry support to CommandBus
   fix: handle null payload in EventBus
   docs: update README with RabbitMQ configuration
   ```

6. **Push** your branch and open a **pull request** against `main`.

## Development Prerequisites

- Java 21 or later
- Docker (for Testcontainers-based integration tests)

## Running Tests

```bash
# All tests
./gradlew test

# A specific module
./gradlew :spring-boot-cqrs-core:test

# A single test class
./gradlew :spring-boot-cqrs-core:test --tests "MyTestClass"
```

## Code Style

This project uses [Google Java Format](https://github.com/google/google-java-format) enforced via Spotless. Run `./gradlew spotlessApply` to auto-format before committing.

## Reporting Issues

Please use the [issue templates](https://github.com/borja-glez/spring-boot-cqrs/issues/new/choose) for bug reports and feature requests.
