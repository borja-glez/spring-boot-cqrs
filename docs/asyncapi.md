# AsyncAPI Contracts

`spring-boot-cqrs-asyncapi-test` checks the messages of an application against an
[AsyncAPI 3.0](https://www.asyncapi.com/docs/reference/specification/v3.0.0) document. The
document stays the source of truth for what a service sends and receives; the Java message
classes are written by hand, and a test fails when the two drift apart.

## Table of Contents

- [Installation](#installation)
- [Naming messages in the contract](#naming-messages-in-the-contract)
- [What is verified](#what-is-verified)
- [Writing the test](#writing-the-test)
- [Sampling replies](#sampling-replies)
- [Payload schemas](#payload-schemas)
- [Limits](#limits)

## Installation

**Gradle**

```kotlin
testImplementation("com.borjaglez.cqrs:spring-boot-cqrs-asyncapi-test:0.6.0")
```

**Maven**

```xml
<dependency>
    <groupId>com.borjaglez.cqrs</groupId>
    <artifactId>spring-boot-cqrs-asyncapi-test</artifactId>
    <version>0.6.0</version>
    <scope>test</scope>
</dependency>
```

The module reads the document and validates payloads with
[networknt json-schema-validator](https://github.com/networknt/json-schema-validator) 2.x, the line
that uses Jackson 2 internally. Jackson is not part of its API: the verifier only sees the bytes
your `MessageSerializer` produces, so a Boot 4 application that serializes with Jackson 3 is
verified the same way. It does not depend on Spring Boot, and failures are plain `AssertionError`s,
so it works with any test framework.

**json-schema-validator on the test classpath.** The module needs json-schema-validator 2.x on the
test classpath (it is built against 2.0.8), and it shares it with other 2.x users, such as
Atlassian's `openapi-request-validator` 3.x for REST contracts. The 1.x line and the 3.x line (the
Jackson 3 one) have a different API: when a build resolves one of them, the verifier fails with a
`NoClassDefFoundError` or a `NoSuchMethodError` from `com.networknt.schema`. Check the version the
tests get:

```bash
./gradlew dependencyInsight --configuration testRuntimeClasspath --dependency com.networknt:json-schema-validator
mvn dependency:tree -Dincludes=com.networknt:json-schema-validator
```

When it is not 2.x, align it. With Gradle, pin it for the tests:

```kotlin
testImplementation("com.networknt:json-schema-validator") {
    version { strictly("2.0.8") }
}
```

With Maven, declare `com.networknt:json-schema-validator` 2.0.8 with `<scope>test</scope>` in the
project: the nearest declaration wins. A tool that only works with json-schema-validator 1.x or 3.x
cannot share the test classpath with the module; keep its tests in another module.

**Jackson 2 version.** json-schema-validator 2.0.8 depends on Jackson 2.22.x and works with Jackson
2.15 or later. Maven, and Gradle with the Spring dependency-management plugin, keep the Jackson
version of the Spring Boot BOM. A Gradle build that imports the BOM with `platform(...)`, or uses
no BOM, gets Jackson 2.22.x on the test runtime classpath; to test with the application's Jackson
version, import the BOM with `enforcedPlatform(...)` or apply the Spring dependency-management
plugin.

## Naming messages in the contract

The `name` of every AsyncAPI Message Object is the **logical name** of the message, the one
`MessageNamingStrategy` gives it from `@CqrsMessage` (`[prefix.]service.version.type.module.name`,
see [Message names](../README.md#message-names-and-cqrsmessage)). A message without a `name` is
rejected when the document is loaded. Replies and other payloads that have no logical name get the
name the contract chooses.

```java
@CqrsMessage(service = "orders", module = "order", name = "order-placed")
public class OrderPlaced extends Event { ... }   // with cqrs.naming.prefix=shop
```

```yaml
asyncapi: 3.0.0
info:
  title: Orders service
  version: 1.0.0
channels:
  orderEvents:
    address: shop.orders.event
    messages:
      orderPlaced:
        name: shop.orders.1.event.order.order-placed
        payload:
          $ref: 'schemas/order-placed.json'
  orderQueries:
    address: shop.orders.query
    messages:
      findOrder:
        name: shop.orders.1.query.order.find-order
        payload:
          $ref: 'schemas/find-order.json'
  orderQueryReplies:
    address: null
    messages:
      orderView:
        name: order-view                # a reply: the contract names it
        payload:
          $ref: 'schemas/order-view.json'
operations:
  publishOrderPlaced:
    action: send
    channel:
      $ref: '#/channels/orderEvents'
  findOrder:
    action: receive
    channel:
      $ref: '#/channels/orderQueries'
    reply:
      channel:
        $ref: '#/channels/orderQueryReplies'
```

`AsyncApiContract` reads the operations from the point of view of the service that owns the
document:

| Operation | Role of its messages |
|---|---|
| `action: send` | `SENT` |
| `action: receive` | `RECEIVED` |
| `reply` of a `receive` | `REPLY_SENT` |
| `reply` of a `send` | `REPLY_RECEIVED` |

An operation (or a reply) that lists no `messages` takes every message of its channel.
`contract.messages()` returns each message once per name and role, as
`ContractMessage(name, role, channel)` where `channel` is the key of the channel in `channels`,
ordered by channel and name. `contract.message(name)` finds one by name.

## What is verified

`AsyncApiVerifier` collects sample messages and runs four independent verifications. Each throws a
single `AssertionError` listing **every** violation, one line per violation prefixed by the message
name:

```
3 contract violations:
- shop.orders.1.command.order.place-order: $.customerId: must be at least 1 characters long
- shop.orders.1.event.order.order-placed: $.total.currency: does not match the regex pattern ^[A-Z]{3}$
- order-view: $.status: does not have a value in the enumeration ["PLACED", "SHIPPED", "CANCELLED"]
```

| Verification | Fails when |
|---|---|
| `verifyPayloads()` | a sample, serialized with the application's `MessageSerializer`, is not valid against the payload schema of the contract message with its name, or the contract has no message with that name |
| `verifyCoverage()` | a contract message has no sample, or a sample has a name the contract does not declare |
| `verifyClosedSchemas()` | an object payload schema (`type: object` or with `properties`), after following its top-level `$ref`s, does not declare `additionalProperties: false` |
| `verifyHandlers(CqrsIntrospection)` | a `RECEIVED` message has no handler with that `messageName`, or only handlers marked `remote = false` |

`verifyClosedSchemas()` is what makes `verifyPayloads()` catch a field added in Java but not in the
contract: an open schema accepts any extra property. `verifyCoverage()` makes sure every message of
the contract is exercised and that no message is sent or received without being in the contract.

## Writing the test

Use the application's own serializer and naming strategy so the samples are serialized and named
exactly as on the wire. In a Spring Boot test they are beans, together with `CqrsIntrospection`:

```java
@SpringBootTest
class OrdersContractTest {

    private static final AsyncApiContract CONTRACT =
        AsyncApiContract.fromClasspath("asyncapi/orders.yaml");

    @Autowired MessageSerializer serializer;
    @Autowired MessageNamingStrategy naming;
    @Autowired CqrsIntrospection introspection;

    private AsyncApiVerifier verifier() {
        return AsyncApiVerifier.of(CONTRACT, serializer, naming)
            .sample(new PlaceOrder("customer-1", new Money(new BigDecimal("19.90"), "EUR")))
            .sample(new OrderPlaced("order-1", "customer-1", new Money(new BigDecimal("19.90"), "EUR")))
            .sample(new FindOrder("order-1"))
            .sample("order-view", new OrderView("order-1", "PLACED", new Money(BigDecimal.TEN, "EUR")));
    }

    @Test
    void payloadsMatchTheContract() {
        verifier().verifyPayloads();
    }

    @Test
    void everyContractMessageIsSampled() {
        verifier().verifyCoverage();
    }

    @Test
    void payloadSchemasRejectUnknownFields() {
        verifier().verifyClosedSchemas();
    }

    @Test
    void receivedMessagesHaveHandlers() {
        verifier().verifyHandlers(introspection);
    }
}
```

Without Spring, build the serializer and the naming strategy yourself, for example
`new JacksonMessageSerializer(objectMapper)` and `new DefaultMessageNamingStrategy("shop")`, with an
`ObjectMapper` configured like the application's. `AsyncApiContract.load(Path)` reads the document
from the file system instead of the classpath, for example from a directory shared with other
services.

## Sampling replies

`sample(Object)` names commands, queries and events with the naming strategy, so it rejects any
other type. Replies, such as the result of `dispatchAndReceive` or `ask`, have no logical name:
sample them with the name the contract gives them.

```java
verifier
    .sample(new FindOrder("order-1"))                                // shop.orders.1.query.order.find-order
    .sample("order-view", new OrderView("order-1", "PLACED", total)) // REPLY_SENT
    .sample("stock-level", new StockLevel("sku-1", 3))               // REPLY_RECEIVED
    .sample("place-order-result", "order-1");                        // a String result
```

## Payload schemas

**What is serialized.** The sample is serialized whole, as it would be sent, so the payload schema
must declare the fields of the base classes too: `commandId` for a `Command`, `queryId` for a
`Query`, `eventId` and `occurredOn` for an `Event`. With `additionalProperties: false` a schema that
omits them fails.

**References.** A payload may be inline, a `$ref` to `#/components/schemas/...`, or a `$ref` to
another file. Relative references resolve against the location of the document, both with
`load(Path)` and with `fromClasspath(String)`, and inside a schema file against that file: a
`schemas/order.json` can reference `money.json` next to it.

**Schema formats.** By default a payload is an AsyncAPI Schema Object, read as JSON Schema
draft-07. A schema file that declares `$schema` uses that dialect, and a Multi Format Schema Object
selects it with `schemaFormat`:

```yaml
payload:
  schemaFormat: application/schema+json;version=2020-12
  schema:
    type: object
    ...
```

`application/schema+json` and `application/schema+yaml` with `draft-04`, `draft-06`, `draft-07`,
`2019-09` and `2020-12` are supported. Any other format, such as Avro, cannot be verified and is
reported as a violation by `verifyPayloads()` and `verifyClosedSchemas()`.

## Limits

- Only AsyncAPI 3 documents are read; a 2.x document is rejected.
- References between operations, channels, replies and messages must point inside the document.
  An operation's `channel` must be `#/channels/<channel>` and its `messages` (and those of its
  `reply`) `#/channels/<channel>/messages/<message>`, as the specification requires. Only payload
  schemas may reference other files.
- An absolute `$id`, an absolute `$ref` or a custom `$schema` IRI in a payload schema is fetched
  over the network, so the check needs access to it and can drift from your local files. Prefer
  relative references.
- A Multi Format Schema Object (`schemaFormat`) is recognised in the document, or in
  `components` of it, not in a separate file referenced by `$ref`.
- `verifyClosedSchemas()` checks the top-level object of each payload, not nested objects or
  `allOf` compositions.
