# Actuator Endpoints

When `spring-boot-starter-actuator` is on the consumer's classpath, the boot3 and boot4 starters expose two CQRS-specific operational endpoints out of the box.

## Table of Contents

- [Setup](#setup)
- [`/actuator/cqrs`](#actuatorcqrs)
- [`/actuator/cqrs/handlers/{kind}`](#actuatorcqrshandlerskind)
- [`/actuator/info`](#actuatorinfo)
- [Transport Health](#transport-health)
- [Disabling](#disabling)

## Setup

Add the actuator starter (the spring-boot-cqrs starters already depend on it as `compileOnly`, so no version pin is needed):

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

Then expose the endpoints you want:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cqrs, info, health
```

## `/actuator/cqrs`

Returns a snapshot of all registered handlers, middlewares and message types.

```json
{
  "counts": { "commands": 3, "events": 5, "queries": 2 },
  "handlers": [
    {
      "kind": "command",
      "messageType": "com.acme.CreateOrderCommand",
      "messageName": "orders.command.create",
      "handlerBeanType": "com.acme.OrderCommandHandler",
      "requiresValidation": true
    }
  ],
  "middleware": [
    { "type": "com.borjaglez.cqrs.context.ContextPropagationMiddleware", "order": -2147483638, "observability": false },
    { "type": "com.borjaglez.cqrs.tracing.TracingMiddleware", "order": -2147483638, "observability": true }
  ],
  "messageTypes": [
    "com.acme.CreateOrderCommand",
    "com.acme.OrderCreatedEvent"
  ]
}
```

The data comes straight from `CqrsIntrospection` (the same source that `CqrsIntrospectionLogger` uses to log the registry summary on startup), so what you see in the endpoint is what's actually wired in the running JVM.

## `/actuator/cqrs/handlers/{kind}`

Filter by handler kind. `{kind}` accepts `command`, `event` or `query` (case-insensitive).

```http
GET /actuator/cqrs/handlers/command
```

```json
[
  {
    "kind": "command",
    "messageType": "com.acme.CreateOrderCommand",
    "messageName": "orders.command.create",
    "handlerBeanType": "com.acme.OrderCommandHandler",
    "requiresValidation": true
  }
]
```

Unknown sections or kinds produce a 400 Bad Request.

## `/actuator/info`

When the `info` endpoint is exposed, a `cqrs` section is contributed with the same counts that the dedicated endpoint surfaces:

```json
{
  "cqrs": {
    "commands": 3,
    "events": 5,
    "queries": 2,
    "middleware": 6,
    "messageTypes": 8
  }
}
```

This is useful for dashboards that aggregate `/actuator/info` from many services without having to expose `/actuator/cqrs` publicly.

## Transport Health

This library does **not** ship a custom health indicator. Spring Boot already provides production-quality health indicators for the transports we adapt:

- `RabbitHealthIndicator` (when `spring-boot-starter-amqp` is on the classpath) checks broker connectivity.
- `KafkaHealthIndicator` (when `spring-kafka` is on the classpath) checks cluster reachability.

Both are picked up automatically by Spring Boot's actuator auto-configuration. Their entries appear in `/actuator/health` as `rabbit` and `kafka`.

## Disabling

The endpoint and the info contributor honour Spring Boot's standard endpoint properties — no `cqrs.*` flag is introduced:

```yaml
management:
  endpoint:
    cqrs:
      enabled: false   # turns off /actuator/cqrs and its bean
  info:
    cqrs:
      enabled: false   # turns off the info contributor only
```

Both default to enabled when the actuator starter is on the classpath.
