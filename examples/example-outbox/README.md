# example-outbox

Transactional outbox with `spring-boot-cqrs-jdbc`, PostgreSQL and Kafka (Spring Boot 3).

`CreateOrderCommandHandler` saves the order and publishes `OrderCreatedEvent` through
`OutboxEventBus` in the same transaction. The event is stored as a row of `cqrs_outbox`; after
commit, the outbox relay publishes it through `KafkaEventBus` and marks the row published once
Kafka acknowledged it. `OrderEventHandler` receives it back from Kafka.

## Run

Docker Compose starts PostgreSQL and Kafka (`compose.yml`):

```bash
./gradlew :examples:example-outbox:bootRun
curl -X POST localhost:8082/api/orders -H 'Content-Type: application/json' \
  -d '{"product":"book","quantity":2}'
```

The log shows `Received order-created from Kafka ...`. Inspect the outbox:

```sql
SELECT event_name, attempts, published_at, failed_at FROM cqrs_outbox;
```

Stop the Kafka container and create another order: the row stays pending with growing `attempts`
and is published when Kafka is back.

See [docs/outbox.md](../../docs/outbox.md).
