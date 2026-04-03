# GraalVM Native Image Support

The library supports GraalVM native image compilation out of the box. Two mechanisms ensure that all CQRS annotations, handler classes, and message types are available at runtime in a native image.

## Table of Contents

- [How AOT Support Works](#how-aot-support-works)
- [What Is Registered Automatically](#what-is-registered-automatically)
- [Building a Native Image](#building-a-native-image)
- [Known Limitations](#known-limitations)

## How AOT Support Works

Spring Framework's AOT (Ahead-of-Time) processing runs at build time to generate the metadata that GraalVM needs for reflection, proxies, and resources. The library provides two components that hook into this process:

### CqrsRuntimeHintsRegistrar

**Package:** `com.borjaglez.cqrs.aot`  
**Activated by:** `@ImportRuntimeHints` in `CqrsAutoConfiguration`

Registers all CQRS annotations for reflection so they can be discovered at runtime:

- `@CommandHandler`
- `@HandleCommand`
- `@EventHandler`
- `@HandleEvent`
- `@QueryHandler`
- `@HandleQuery`
- `@CqrsMessage`

All member categories (fields, methods, constructors) are registered for each annotation type.

### CqrsBeanRegistrationAotProcessor

**Package:** `com.borjaglez.cqrs.aot`

A `BeanRegistrationAotProcessor` that runs during Spring AOT processing. For each bean annotated with `@CommandHandler`, `@EventHandler`, or `@QueryHandler`, it:

1. Registers the handler bean class for reflection (`INVOKE_DECLARED_METHODS`, `DECLARED_FIELDS`).
2. Scans for methods annotated with `@HandleCommand`, `@HandleEvent`, or `@HandleQuery`.
3. Registers each handler method's parameter types (the `Command`, `Event`, or `Query` subclass) for full reflection (all member categories).

This ensures that:
- Handler beans can be instantiated and their methods invoked via reflection/MethodHandle.
- Message classes can be serialized/deserialized by Jackson.

### RabbitMqCqrsRuntimeHints

**Package:** `com.borjaglez.cqrs.rabbitmq.aot`  
**Activated by:** `@ImportRuntimeHints` in `RabbitMqCqrsAutoConfiguration`

Registers the RabbitMQ consumer and adapter classes for reflection:

- `RabbitMqCommandConsumer`
- `RabbitMqEventConsumer`
- `RabbitMqQueryConsumer`
- `ExtendedMessageListenerAdapter`

These classes need reflection access because Spring AMQP's `MessageListenerAdapter` invokes consumer methods dynamically.

## What Is Registered Automatically

| Category | Classes | Registration |
|---|---|---|
| CQRS annotations | All 7 annotation types | `CqrsRuntimeHintsRegistrar` |
| Handler beans | Any class with `@CommandHandler`, `@EventHandler`, or `@QueryHandler` | `CqrsBeanRegistrationAotProcessor` |
| Message types | Parameter types of `@HandleCommand`, `@HandleEvent`, `@HandleQuery` methods | `CqrsBeanRegistrationAotProcessor` |
| RabbitMQ consumers | Consumer and adapter classes | `RabbitMqCqrsRuntimeHints` |

You do **not** need to add any `reflect-config.json` entries or `@RegisterReflectionForBinding` annotations for standard usage.

## Building a Native Image

### With Spring Boot Gradle Plugin

```bash
./gradlew nativeCompile
```

### With Spring Boot Maven Plugin

```bash
mvn -Pnative native:compile
```

### With GraalVM directly

```bash
./gradlew bootJar
native-image -jar build/libs/my-app.jar
```

### Running the native image

```bash
./build/native/nativeCompile/my-app
```

The native image starts significantly faster and uses less memory than the JVM version. All CQRS features -- handler discovery, bus dispatching, middleware, and RabbitMQ integration -- work identically in native mode.

## Known Limitations

1. **Dynamic handler registration at runtime** is not supported in native images. All handlers must be known at build time during AOT processing. This is the standard Spring AOT constraint.

2. **Custom `MessageSerializer` implementations** that use reflection internally may need their own runtime hints. The built-in `JacksonMessageSerializer` works without additional configuration because Jackson's native image support is handled by Spring Boot.

3. **Custom `BusMiddleware` implementations** that use reflection or dynamic proxies should register their own hints via `RuntimeHintsRegistrar`.

4. **Lambda-based handlers** are not supported. Handlers must be concrete classes annotated with the appropriate class-level annotation.

5. The library requires Spring Boot 3.x or later for AOT support (both boot3-starter and boot4-starter are supported). Spring Boot 2.x does not support `RuntimeHintsRegistrar` or `BeanRegistrationAotProcessor`.
