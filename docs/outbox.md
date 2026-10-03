# Transactional Outbox

`spring-boot-cqrs-jdbc` ships a transactional outbox: an event published through
`OutboxEventBus` is stored in the caller's database transaction and published to the broker after
commit by a relay. The event leaves the process **if and only if** the transaction commits, and a
broker outage delays it instead of losing it.

## Enabling it

Add `spring-boot-cqrs-jdbc` and a transport (`spring-boot-cqrs-kafka` or
`spring-boot-cqrs-rabbitmq`), then:

```yaml
cqrs:
  outbox:
    enabled: true
```

Adding the dependency alone changes nothing. Inject `OutboxEventBus` where an event must reach
other services; the primary `EventBus` (local, after-commit) is unchanged.

```java
@CommandHandler
class PlaceOrderHandler {

  private final OrderRepository orders;
  private final OutboxEventBus outbox;

  @Transactional
  @HandleCommand
  void handle(PlaceOrder command) {
    Order order = orders.save(Order.place(command));
    outbox.publish(new OrderPlaced(order.id()));
  }
}
```

`publish` throws `IllegalStateException` when no transaction is active.

## How it works

1. `OutboxEventBus.publish` inserts a row: event id, logical name (`@CqrsMessage` /
   `MessageNamingStrategy`), Java class name, payload (`MessageSerializer`), and the
   `MessageContext` plus the current trace.
2. The relay (one thread per instance) locks the oldest pending rows with
   `FOR UPDATE SKIP LOCKED`, publishes each through the target event bus inside the stored context,
   and marks it published once the bus returned. Kafka returns after the broker acknowledged the
   record; RabbitMQ does so only with publisher confirms (see below).
3. Published rows are deleted after `cqrs.outbox.retention`.

The relay publishes through `KafkaEventBus` or `RabbitMqEventBus`, so outbound middlewares, context
headers and the logical name header work as for any other event. Local `@HandleEvent` handlers
receive the event through the application's own consumer, like other services.

### Choosing the event bus

The relay uses the only `EventBus` bean other than `springEventBus` and the outbox bus. With none
or several, startup fails; name the bean:

```yaml
cqrs:
  outbox:
    relay:
      event-bus: kafkaEventBus
```

### RabbitMQ needs publisher confirms

Without confirms, `RabbitMqEventBus` returns before the broker accepted the message and the row is
marked published too early. The application logs a warning at startup. Enable them:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
cqrs:
  rabbitmq:
    events:
      confirms:
        enabled: true
```

## Guarantees

- **At least once.** If the process dies after the broker acknowledged an event and before the
  relay committed, the event is published again. Make consumers idempotent with
  [`@Idempotent`](idempotency.md).
- **Order.** With one relaying instance, events are published in insertion order (except around a
  row that was set aside, below). Several instances never publish the same row concurrently, but
  their batches run in parallel and are not ordered against each other. When strict order matters,
  set `cqrs.outbox.relay.enabled=false` on every instance but one.
- **Broker outages.** A failure of the event bus (broker down, timeout, rejection) stops the
  batch, increments `attempts`, stores `last_error`, and is retried at the next run, forever. No
  event is skipped.
- The batch transaction stays open while it publishes (it holds the row locks): keep
  `batch-size` moderate.
- **Kafka timeouts.** While the broker is unreachable, a Kafka send blocks for up to
  `max.block.ms` (60 s by default) and waits up to `delivery.timeout.ms` (120 s) for the
  acknowledgement, and the batch transaction and its row locks stay open that long. Lower them
  for the relaying application (they apply to its Kafka producer; `delivery.timeout.ms` must be at
  least `linger.ms` + `request.timeout.ms`):

  ```yaml
  spring:
    kafka:
      producer:
        properties:
          max.block.ms: 10000
          request.timeout.ms: 10000
          delivery.timeout.ms: 30000
  ```

## Rows that cannot be read

A row whose class cannot be found or linked (by logical name, then by class name), whose payload
cannot be deserialized, or whose stored message context or trace cannot be read is retried like
any failure: the batch stops there and the next run tries again. Each such failure increments
`read_failures`; `attempts` counts every failed attempt, event bus failures included. Once
`read_failures` reaches `cqrs.outbox.relay.max-attempts` (10) the row is **set aside**:
`failed_at` is set, an ERROR is logged, and later rows continue. Event bus failures never set a
row aside. Set-aside rows are never deleted automatically. To retry one:

```sql
UPDATE cqrs_outbox SET failed_at = NULL, attempts = 0, read_failures = 0 WHERE event_id = '...';
```

The retry window is about `max-attempts` x `interval` (10 seconds by default) and is shared by all
instances: every relay that fails to read the row counts a failure. During a rolling deploy, old
instances that do not have a new event class yet can set its rows aside before the new instances
take over. To avoid it, raise `cqrs.outbox.relay.max-attempts`, ship new event classes one release
before you publish them, or set `cqrs.outbox.relay.enabled=false` on the old instances.

## Renaming event classes

The row stores the logical name and the class name. The relay looks the logical name up among
the events this application handles and the `@CqrsMessage` events of the packages listed in
`cqrs.aot.message-packages`, then falls back to the class name. Annotate outbox events with
`@CqrsMessage` and list their packages to rename or move classes while rows are pending.

## Schema

The table is created according to `cqrs.jdbc.initialize-schema` (`embedded` by default, so only
embedded databases; use `always` or your migrations for PostgreSQL). Scripts ship in the jar:
`com/borjaglez/cqrs/jdbc/schema-outbox-postgresql.sql` and `schema-outbox-h2.sql`. For Flyway or
Liquibase, copy the PostgreSQL script, replace `${table}` with the table name and `${index}` with
the unqualified table name, and set `cqrs.jdbc.initialize-schema=never`:

```sql
CREATE TABLE IF NOT EXISTS cqrs_outbox (
  id           BIGSERIAL     PRIMARY KEY,
  event_id     VARCHAR(64)   NOT NULL UNIQUE,
  event_name   VARCHAR(255)  NOT NULL,
  event_class  VARCHAR(512)  NOT NULL,
  payload      BYTEA         NOT NULL,
  context      BYTEA,
  created_at   TIMESTAMP     NOT NULL,
  published_at TIMESTAMP,
  failed_at    TIMESTAMP,
  attempts     INT           NOT NULL DEFAULT 0,
  read_failures INT          NOT NULL DEFAULT 0,
  last_error   VARCHAR(2000)
);
CREATE INDEX IF NOT EXISTS cqrs_outbox_pending ON cqrs_outbox (id) WHERE published_at IS NULL AND failed_at IS NULL;
CREATE INDEX IF NOT EXISTS cqrs_outbox_published ON cqrs_outbox (published_at) WHERE published_at IS NOT NULL;
```

`event_id` holds up to 64 characters: publishing an event whose custom event id is longer fails
the insert, and with it the caller's transaction. `created_at`, `published_at` and `failed_at` are
`TIMESTAMP` without time zone written from an `Instant` in the JVM's time zone; run every instance
in the same time zone (UTC is simplest) so that the retention cutoffs of all instances agree.

PostgreSQL and H2 are supported. Another database works if you create an equivalent table and it
accepts `SELECT ... FETCH FIRST n ROWS ONLY FOR UPDATE SKIP LOCKED`.

On PostgreSQL and other databases the relay locks rows with
`SELECT ... ORDER BY id FETCH FIRST n ROWS ONLY FOR UPDATE SKIP LOCKED`. H2 does not skip locked rows
with that form, so on H2 the relay uses a subquery:
`... WHERE id IN (SELECT id ... ORDER BY id FETCH FIRST n ROWS ONLY) ... FOR UPDATE SKIP LOCKED`.

The outbox needs a single `DataSource` and a single `PlatformTransactionManager` (or one marked
`@Primary`), like the JDBC idempotency store. Under JPA the row joins the JPA transaction.

## Tracing

With Micrometer Tracing (a `Tracer` and a `Propagator` bean), the trace of the publishing
transaction is stored with the row and the relay publishes inside a `cqrs.outbox.relay` span that
continues it, so the broker send and the consumer stay in the original trace (enable
`spring.kafka.template.observation-enabled` for Kafka headers).

## Native images

List the packages of outbox events in `cqrs.aot.message-packages`: their binding hints and the
logical-name index (`META-INF/cqrs/outbox-event-names.properties`) are generated at build time.

## Configuration

| Property | Default | Description |
|---|---|---|
| `cqrs.outbox.enabled` | `false` | Store events published through `OutboxEventBus` and relay them |
| `cqrs.outbox.retention` | `7d` | How long published rows are kept |
| `cqrs.outbox.relay.enabled` | `true` | Whether this instance relays; instances that only write can turn it off |
| `cqrs.outbox.relay.event-bus` | unset | Bean name of the event bus to publish through |
| `cqrs.outbox.relay.interval` | `1s` | Delay between two relay runs |
| `cqrs.outbox.relay.batch-size` | `100` | Rows relayed per transaction |
| `cqrs.outbox.relay.max-attempts` | `10` | Failed reads (`read_failures`) after which an unreadable row is set aside; bus failures never set a row aside |
| `cqrs.jdbc.outbox.table-name` | `cqrs_outbox` | Outbox table; may be schema-qualified |
| `cqrs.jdbc.outbox.cleanup-enabled` | `true` | Delete published rows older than the retention |
| `cqrs.jdbc.outbox.cleanup-interval` | `1h` | Delay between two cleanups |
