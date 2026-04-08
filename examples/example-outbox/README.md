# example-outbox

Spring Boot 3 example showing the **Outbox Pattern** as an optional extension on top of `spring-boot-cqrs`.

## What it demonstrates

- a transactional command handler that saves domain state and an outbox row in the **same transaction**
- a scheduled outbox publisher that reads pending rows and publishes events through `EventBus`
- local event handling after the publisher marks rows as published

## Important note

This example sets `cqrs.events.transactional=false` on purpose.

Why? Because the **outbox itself** already controls publication timing. If the transactional event bus also deferred publication again, the example would mark outbox rows as published before the actual send happened.

## Run

```bash
./gradlew :examples:example-outbox:bootRun
```

## Try it

Create an order:

```bash
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"product":"Keyboard","quantity":2}'
```

List orders:

```bash
curl http://localhost:8082/api/orders
```
