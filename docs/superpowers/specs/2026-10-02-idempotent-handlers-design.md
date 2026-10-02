# Idempotent handling of redelivered commands and events — design

Issue: #2. Status: approved design, ready for an implementation plan.

## Goal

RabbitMQ and Kafka deliver at least once. A handler that is not idempotent applies the same effect
twice when a message is redelivered. The library gives every message an identity (`commandId`,
`eventId`) but nothing uses it. This design adds opt-in, per-handler deduplication.

Invariants that must hold:

- A redelivered message whose previous processing succeeded is not applied again.
- A message whose previous processing failed is applied on redelivery (no "poisoned" marker).
- Two concurrent deliveries of the same id apply the effect at most once.
- The store does not grow without bound.
- Applications that do not use the feature see no behaviour change.

## Decisions (answers to the questions in #2)

| # | Question | Decision |
|---|----------|----------|
| 1 | Scope | Commands and events. Queries are out of scope (reads). |
| 2 | Granularity | Per handler: the key is `(handlerId, messageId)`. |
| 3 | Transactional coupling | The SPI supports both. The JDBC store runs the marker insert and the handler in one transaction (exactly-once effect for database work). The in-memory store uses acquire / complete / release with a lease and a documented duplicate window. |
| 4 | `dispatchAndReceive` duplicates | Throw `DuplicateMessageException`. No result storage. |
| 5 | JDBC store location | New module `spring-boot-cqrs-jdbc`; the outbox (#3) can be added to it later. |
| 6 | Retention | 7 days by default. JDBC: hourly cleanup task, can be disabled. In-memory: lazy TTL expiry; in-progress lease 5 minutes. |
| — | Mechanism | `@Idempotent` method annotation applied by the registries per handler, plus a public `IdempotentInvoker` bean for manual use (e.g. inside a plain `@KafkaListener`). |
| — | Dispatch phase | Applied wherever the handler is invoked, local or remote. Local duplicates do not occur in practice, so the cost is one marker write. |

### Why per handler

`EventHandlerRegistry.handle` invokes the handlers of an event in a loop and aborts on the first
failure. The handlers before it have already run; the ones after it never run. RabbitMQ re-sends
the whole event to the retry exchange (same `eventId`) and Kafka seeks back, so a redelivery
re-runs the handlers that already succeeded. A middleware sees one message for all handlers and
cannot fix this; `RetryMiddleware` skips events for the same reason.

## Core API — package `com.borjaglez.cqrs.idempotency`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
  /** Stable handler id. Defaults to "<beanName>#<methodName>". Set it to survive renames. */
  String name() default "";
}

public interface IdempotencyStore {
  Acquisition tryAcquire(String handlerId, String messageId);   // ACQUIRED or DUPLICATE
  void complete(String handlerId, String messageId);
  void release(String handlerId, String messageId);
  default <T> T runInScope(Supplier<T> work) { return work.get(); }
}

public enum Acquisition { ACQUIRED, DUPLICATE }

public final class IdempotentInvoker {
  public IdempotentInvoker(IdempotencyStore store);
  /** Runs the effect at most once per (handlerId, messageId). */
  public <T> Outcome<T> invoke(String handlerId, String messageId, Supplier<T> effect);
}

public record Outcome<T>(boolean duplicate, T result) { }   // result is null when duplicate

public class DuplicateMessageException extends RuntimeException { ... }
```

`IdempotentInvoker.invoke` runs, inside `store.runInScope`:

1. `tryAcquire`. On `DUPLICATE`, return `Outcome(true, null)` without running the effect.
2. On `ACQUIRED`, run the effect, then `complete`, and return `Outcome(false, result)`.
3. If the effect throws, `release` and rethrow the same exception.

`Outcome` keeps "duplicate" apart from "the handler returned `null`", which the command registry
needs to decide between returning `null` and throwing `DuplicateMessageException`.

### Registries and discovery

- `EventHandlerRegistry.HandlerInfo` and `CommandHandlerRegistry.HandlerInfo` gain a nullable
  `String idempotencyKey` (the handler id). `null` means not idempotent and the invocation path is
  exactly today's.
- `BeanPostProcessorHandlerDiscoverer` reads `@Idempotent` on `@HandleEvent` / `@HandleCommand`
  methods and passes the handler id (explicit `name` or `beanName#methodName`) to `register`. New
  `register` overloads are added; the existing ones keep working.
- The registries receive the `IdempotentInvoker` (nullable). Registering an `@Idempotent` handler
  when no invoker is available fails at startup with a message naming the handler and telling the
  user to add `spring-boot-cqrs-jdbc` or set `cqrs.idempotency.store=in-memory`.
- `@Idempotent` on a `@HandleQuery` method, or on a method without a handler annotation, fails at
  startup.
- Event duplicate: the handler is skipped and a `DEBUG` log names handler and `eventId`. The loop
  continues with the next handler.
- Command duplicate: a `void` handler is skipped and the registry returns `null`; a handler with a
  return type throws `DuplicateMessageException`.
- The message id is `Command.getCommandId()` / `Event.getEventId()`; handler parameters are already
  required to extend `Command` / `Event`.

### In-memory store (core)

`InMemoryIdempotencyStore(Duration retention, Duration lease, Clock clock)`:

- `tryAcquire`: atomic `compute` on a `ConcurrentHashMap`. Absent, expired, or `IN_PROGRESS` with an
  expired lease → `IN_PROGRESS` with `now + lease`, `ACQUIRED`. Otherwise `DUPLICATE`.
- `complete`: `COMPLETED`, expires at `now + retention`.
- `release`: remove.
- Expired entries are purged lazily on access and by a full sweep every 1,000 `tryAcquire` calls,
  so memory stays bounded.
- Guarantee: per process only. A crash between effect and `complete` loses the marker (duplicate
  window). Documented.

### Interaction with other middleware

- `RetryMiddleware` (#21): a failed attempt releases the key, so an in-process retry re-runs the
  handler. Order between the two does not matter.
- #59 (sending-side middleware): deduplication lives in the registries, which only run on the
  consuming side, so it is unaffected.

## Module `spring-boot-cqrs-jdbc`

- Conventions as the transport modules: `cqrs-boot3-library-conventions`, `cqrs-publish-conventions`,
  `cqrs-test-conventions`; `api(project(":spring-boot-cqrs-core"))`, `api(libs.spring.jdbc)`,
  `api(libs.spring.boot.autoconfigure)` (new catalog entry `spring-jdbc`, plus
  `testcontainers-postgresql` and the PostgreSQL driver for tests); own `AutoConfiguration.imports`. Works on Boot 3 and
  Boot 4 (checked by `verifyBoot4Compatibility`).
- Registered in the root `quality`, `coverage`, `spotlessCheckAll` and `spotlessApplyAll`, and in
  `settings.gradle.kts`.

### Schema

One portable script, shipped in the jar at
`com/borjaglez/cqrs/jdbc/schema-idempotency.sql` so Flyway/Liquibase users can copy it:

```sql
CREATE TABLE cqrs_processed_message (
  handler_id   VARCHAR(255) NOT NULL,
  message_id   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMP    NOT NULL,
  PRIMARY KEY (handler_id, message_id)
);
CREATE INDEX cqrs_processed_message_at ON cqrs_processed_message (processed_at);
```

Tested on H2 and PostgreSQL; MySQL compatibility is documented, not tested. The table name is
configurable; the script uses the default name.

### `JdbcIdempotencyStore`

- `runInScope`: `TransactionTemplate` with `PROPAGATION_REQUIRED` on the application's
  `PlatformTransactionManager`. A handler's own `@Transactional` joins it; with JPA the
  `JdbcTemplate` shares the connection.
- `tryAcquire`: `INSERT` the marker inside a savepoint. On `DuplicateKeyException`, roll back to the
  savepoint and return `DUPLICATE`. The savepoint keeps PostgreSQL from aborting an outer
  transaction (local dispatch inside the caller's transaction).
- A concurrent delivery blocks on the uncommitted row; after the first commits it gets the
  duplicate key and skips; if the first rolls back, its insert succeeds and it runs the effect.
- `complete` and `release`: no-ops; commit and rollback do the work.
- Risk: savepoints under `JpaTransactionManager` depend on the JPA dialect. Covered by a test; the
  fallback is a per-dialect insert-if-absent statement (`ON CONFLICT DO NOTHING`).

### Cleanup

`JdbcIdempotencyCleanup`, a `SmartLifecycle` bean with its own single-thread scheduler (no
dependency on `@EnableScheduling`): every `cleanup-interval` it runs
`DELETE FROM <table> WHERE processed_at < ?` with `now - retention`. Safe with several instances.

### Auto-configuration

`CqrsJdbcIdempotencyAutoConfiguration`:

- `@AutoConfiguration(afterName = {...})` listing the DataSource and transaction-manager
  auto-configurations of both Boot 3 and Boot 4 (packages differ).
- `@ConditionalOnSingleCandidate(DataSource.class)` and `PlatformTransactionManager`.
- Store `@ConditionalOnMissingBean(IdempotencyStore.class)`, skipped when
  `cqrs.idempotency.store=in-memory`.
- Schema initializer with `ResourceDatabasePopulator` according to `cqrs.jdbc.initialize-schema`
  (`embedded` initializes only embedded databases).
- Cleanup bean when `cqrs.jdbc.idempotency.cleanup-enabled=true`.

## Starters (Boot 3 and Boot 4, identical)

`CqrsIdempotencyAutoConfiguration`:

- `InMemoryIdempotencyStore` only when `cqrs.idempotency.store=in-memory`.
- `IdempotentInvoker` when an `IdempotencyStore` bean exists; passed to both registries.
- `@CqrsTest` (test module) sets `cqrs.idempotency.store=in-memory` by default.

## Properties

| Property | Default | Module |
|----------|---------|--------|
| `cqrs.idempotency.store` | unset (`in-memory` to opt in to the in-memory store) | starters |
| `cqrs.idempotency.retention` | `7d` | starters, read by both stores |
| `cqrs.idempotency.in-memory.lease` | `5m` | starters |
| `cqrs.jdbc.initialize-schema` | `embedded` (`embedded`, `always`, `never`) | jdbc |
| `cqrs.jdbc.idempotency.table-name` | `cqrs_processed_message` | jdbc |
| `cqrs.jdbc.idempotency.cleanup-enabled` | `true` | jdbc |
| `cqrs.jdbc.idempotency.cleanup-interval` | `1h` | jdbc |

## Testing

Regression first, failing before the change:

1. `EventHandlerRegistryTest`: event with two `@Idempotent` handlers, the second fails on the first
   delivery; on redelivery of the same `eventId` the first is not re-applied and the second runs.
   Also: the same event twice is applied once per handler.
2. `CommandHandlerRegistryTest`: duplicate `void` command skipped; duplicate command with a result
   throws `DuplicateMessageException`.
3. `IdempotentInvokerTest`, `InMemoryIdempotencyStoreTest`: failure leaves no marker; N concurrent
   threads on one id apply the effect once; lease expiry; TTL expiry and sweep (injected `Clock`).
4. Discoverer: default and explicit handler id; startup failure without a store; `@Idempotent` on a
   query handler rejected.
5. `JdbcIdempotencyStoreTest` (H2) and a PostgreSQL Testcontainers integration test: rollback leaves
   no marker; two concurrent transactions apply once; duplicate inside an outer transaction keeps
   it usable (also with `JpaTransactionManager`); cleanup by retention; `initialize-schema` modes;
   custom table name.
6. RabbitMQ Testcontainers integration (in `spring-boot-cqrs-rabbitmq`, using the jdbc module with H2
   as a test dependency): an event with two handlers, one fails once; the retry exchange redelivers;
   the successful handler is not re-applied.
7. Auto-configuration tests in both starters and in the jdbc module; `verifyBoot3Compatibility` and
   `verifyBoot4Compatibility`.

`./gradlew quality` green with 100% line and branch coverage.

## Documentation

- New `docs/idempotency.md`: semantics, per-store guarantees, in-memory duplicate window, handler id
  stability, interaction with retry (#21) and consuming-side only (#59), schema for Flyway.
- `docs/configuration.md`: the properties above. Link from `docs/middleware.md` and the README.

## Out of scope

- Exactly-once transport semantics (Kafka transactions, publisher confirms).
- The outbox (#3).
- Storing and returning results for duplicate commands.
- Query deduplication.
- `KafkaEventConsumer` calling `registry.handle` instead of `handleRemote` (separate issue).
