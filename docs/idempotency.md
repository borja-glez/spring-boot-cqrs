# Idempotent Handlers

RabbitMQ and Kafka deliver messages at least once. RabbitMQ re-sends a failed message through the
retry queue; Kafka seeks back after an error or a rebalance. A handler that is not idempotent then
applies the same effect twice. `@Idempotent` makes a command or event handler run at most once per
message.

## Usage

```java
@EventHandler
class StockProjector {

  @HandleEvent
  @Idempotent
  @Transactional
  public void on(ProductPublished event) {
    stock.increment(event.getProductId());
  }
}
```

Add a store: `spring-boot-cqrs-jdbc` with a `DataSource` (production), or
`cqrs.idempotency.store=in-memory` (tests, single instance). An application with `@Idempotent`
handlers and no store fails at startup.

The JDBC store is configured only when the application has a single `DataSource` and a single
`PlatformTransactionManager`, or one of each marked `@Primary`. A second transaction manager, such as
a `KafkaTransactionManager` or a `RabbitTransactionManager`, makes it back off: mark the JDBC or JPA
transaction manager `@Primary`, or define the `JdbcIdempotencyStore` bean yourself.

```kotlin
implementation("com.borjaglez:spring-boot-cqrs-jdbc:<version>")
```

## Semantics

- **Per handler.** The store remembers `(handler id, message id)`. When one of three handlers of
  an event fails, the redelivery skips the two that succeeded and runs the failed one again.
- **Message id.** `Command.getCommandId()` / `Event.getEventId()`. Both survive serialization, so a
  redelivered message has the id of the original. A new `new MyCommand(...)` has a new id.
- **Handler id.** `<beanName>#<methodName>` by default. Renaming the bean or the method makes the
  handler process again the messages still in the retention window; set
  `@Idempotent(name = "stock-projector")` to keep it stable.
- **Failure.** A handler that throws leaves no marker; the redelivery runs it again.
- **Duplicates.** A duplicate event is skipped (logged at `DEBUG`). A duplicate `void` command is
  skipped and returns `null`. A duplicate command whose handler returns a result throws
  `DuplicateMessageException`; the result is not stored. It is not retried, and the RabbitMQ and
  Kafka consumers drop it (logged at `DEBUG`) when no reply is expected; a caller of
  `dispatchAndReceive` or `dispatchAndWait` receives the error.
- **Local and remote.** It applies wherever the handler runs. Local dispatch creates a new id per
  message, so it costs one marker write and never skips.
- **Consuming side only.** Deduplication runs in the registries, which only run on the consuming
  side; sending-side middleware (`DispatchPhase.OUTBOUND`) is unaffected.
- **Queries** are not deduplicated: `@Idempotent` on a `@HandleQuery` method fails at startup. So
  does `@Idempotent` on a method that is not a `@HandleCommand` or `@HandleEvent` method. Both
  checks only apply to handler beans (classes annotated `@CommandHandler`, `@EventHandler` or
  `@QueryHandler`); elsewhere the annotation is ignored.

## Stores and guarantees

| Store | Guarantee |
|---|---|
| JDBC (`spring-boot-cqrs-jdbc`) | The marker is inserted in the transaction that runs the handler (the library opens one with `REQUIRED`, so the handler's `@Transactional` joins it). The insert runs in a JDBC savepoint on the connection of that Spring-managed transaction, so it works with `DataSourceTransactionManager`, `JdbcTransactionManager` and `JpaTransactionManager` (Spring Data JPA). The marker commits with the handler's database work, or both roll back. Two concurrent deliveries: the second waits for the first's row, then skips; if the first rolls back, the second runs. |
| In-memory (`cqrs.idempotency.store=in-memory`) | Per process. A delivery in progress blocks duplicates for `cqrs.idempotency.in-memory.lease`; a handler that runs longer than the lease lets another delivery run it concurrently. A crash between the handler's effect and the marker lets a redelivery apply the effect again; markers are lost on restart and not shared between instances. |
| Your own | Implement `IdempotencyStore` and declare it as a bean. Override `runInScope` to run the handler in your store's transaction; without it, a failure of `complete()` after the effect gives at-least-once behaviour. |

The transaction manager must manage the same `DataSource` as the store (the default JPA and JDBC
managers of Spring Boot do). If the handler's transaction does not bind a JDBC connection for that
`DataSource` (for example with JTA, or a `JpaTransactionManager` without a `DataSource`), the marker
is written outside the handler's transaction.

Effects outside the database (sending an email, calling an API) are not rolled back with the
marker: if the transaction fails after the call, the redelivery calls again. Make those calls
idempotent on the remote side, or move them after commit.

Do not use `@Transactional(propagation = REQUIRES_NEW)` on an `@Idempotent` handler: its work
commits in a separate transaction, so a later failure leaves the work done without a marker.

## Schema

`cqrs.jdbc.initialize-schema=embedded` (default) creates the table in embedded databases (H2, HSQL,
Derby). For other databases, set `always` or create it with your migration tool from the script
shipped in the jar, `com/borjaglez/cqrs/jdbc/schema-idempotency.sql`:

```sql
CREATE TABLE IF NOT EXISTS cqrs_processed_message (
  handler_id   VARCHAR(255) NOT NULL,
  message_id   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMP    NOT NULL,
  PRIMARY KEY (handler_id, message_id)
);
CREATE INDEX IF NOT EXISTS cqrs_processed_message_at ON cqrs_processed_message (processed_at);
```

The script is tested on PostgreSQL and H2. On MySQL, drop `IF NOT EXISTS` from the index statement.

`message_id` holds up to 64 characters and `handler_id` up to 255. `processed_at` is a `TIMESTAMP`
without time zone written in the JVM's time zone; run every instance in the same time zone (UTC is
simplest) so that the cleanup cutoffs of all instances agree.

`cqrs.jdbc.idempotency.table-name` may be schema-qualified (`audit.processed_message`). The index is
named `<table>_at` after the unqualified table name (`processed_message_at`).

## Retention

Markers older than `cqrs.idempotency.retention` (7 days) are deleted every
`cqrs.jdbc.idempotency.cleanup-interval` (1 hour) by every instance (the delete is idempotent).
Both values must be positive. Disable the cleanup with `cqrs.jdbc.idempotency.cleanup-enabled=false`
and purge with `JdbcIdempotencyStore.deleteProcessedBefore(Instant)` on your own schedule. Keep the
retention longer than the time a message can wait before being redelivered, including replays from a
dead-letter queue.

## With retry

`RetryMiddleware` retries commands in-process. A failed attempt releases the marker, so the next
attempt runs the handler; the order between the two does not matter. With the JDBC store, the
marker of a failed attempt goes away when its transaction rolls back. If the command is dispatched
locally inside the caller's transaction, the attempts share that transaction: the next attempt sees
the marker of the failed one and skips the handler (and the transaction is rollback-only anyway).
Start the transaction inside the retry, as described in
[middleware.md](middleware.md#retrymiddleware).

## Testing

`@CqrsTest` always uses the in-memory store (7 days of retention, 5 minutes of lease). An
`IdempotencyStore` bean declared in a `@CqrsTest` is not used.

## Without the annotation

`IdempotentInvoker` is a bean; use it in code that is not a library handler, such as a plain
`@KafkaListener`. The message id must identify the message, not its content or its key: records
that share a key would be dropped as duplicates. Use an id the producer sets once per message, for
example in a header:

```java
String messageId = new String(record.headers().lastHeader("message-id").value(), UTF_8);
invoker.invoke("audit-listener", messageId, () -> {
  audit.save(record.value());
  return null;
});
```

`record.topic() + "-" + record.partition() + "-" + record.offset()` also identifies a record across
redeliveries, as long as it fits. The message id holds up to 64 characters and the handler id up to
255 (the `message_id` and `handler_id` columns).
