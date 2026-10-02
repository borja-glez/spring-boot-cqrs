# Idempotent Handlers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Opt-in, per-handler deduplication of redelivered commands and events through `@Idempotent`, an `IdempotencyStore` SPI, an in-memory store in core and a transactional JDBC store in a new `spring-boot-cqrs-jdbc` module (issue #2).

**Architecture:** The handler discoverer reads `@Idempotent` and stores a handler id in each registry's `HandlerInfo`. The registries wrap the invocation of such handlers with `IdempotentInvoker`, which asks the store for `(handlerId, messageId)`. The JDBC store runs the marker insert and the handler in one transaction, so the marker commits with the effect. Both starters wire the invoker into the registries after startup through `IdempotencyRegistrar`.

**Tech Stack:** Java 21, Spring Framework 6.2 / Boot 3.5 and Boot 4, spring-jdbc, JUnit 5, AssertJ, Mockito, H2, Testcontainers (PostgreSQL, RabbitMQ), Awaitility, Gradle Kotlin DSL.

**Spec:** `docs/superpowers/specs/2026-10-02-idempotent-handlers-design.md`

## Global Constraints

- Branch: `feat/2-idempotent-handlers` (already created from `origin/main`, holds the spec commit).
- New, opt-in API: applications without `@Idempotent` must see no behaviour change; existing public constructors and `register` overloads keep working.
- 100% JaCoCo line and branch coverage on every library module (`core`, `kafka`, `rabbitmq`, `boot3-starter`, `boot4-starter`, and the new `jdbc`).
- Spotless (Google Java Format), import order `java, javax, jakarta, org, com, io`, no wildcard imports. Run `./gradlew spotlessApplyAll` before each commit.
- Boot 3 and Boot 4 starters stay byte-identical for the files this plan touches (`CqrsProperties.java`, `CqrsIdempotencyAutoConfiguration.java`, `AutoConfiguration.imports`).
- Default handler id: `<beanName>#<methodName>`; explicit `@Idempotent(name = "...")` wins.
- Defaults: `cqrs.idempotency.retention=7d`, `cqrs.idempotency.in-memory.lease=5m`, `cqrs.jdbc.initialize-schema=embedded`, `cqrs.jdbc.idempotency.table-name=cqrs_processed_message`, `cqrs.jdbc.idempotency.cleanup-enabled=true`, `cqrs.jdbc.idempotency.cleanup-interval=1h`.
- The in-memory store is registered only with `cqrs.idempotency.store=in-memory` (and by `@CqrsTest`).
- Duplicate event: skip handler, `DEBUG` log. Duplicate `void` command: return `null`. Duplicate command with a result: `DuplicateMessageException`.
- Commits: Conventional Commits with scope; no `Co-Authored-By`, no "Generated with", no mention of Claude, AI, a model or a session.
- Do not touch `CHANGELOG.md`, `gradle.properties` version or the workflows.

## Review Focus

1. A handler's own `@Transactional(propagation = REQUIRES_NEW)` commits its effect outside the marker's transaction; a later failure leaves the effect without a marker. Expected: documented as unsupported in `docs/idempotency.md` (Task 13).
2. An `@Idempotent` event handler that is skipped because of its SpEL `condition` must not write a marker (the condition is evaluated before the invoker). Test in Task 3.
3. A duplicate key inside a caller's open transaction (local dispatch inside `@Transactional` code) must leave that transaction usable on PostgreSQL. Test in Task 10.
4. A custom `cqrs.jdbc.idempotency.table-name` must be used both by the store and by the schema initializer, and an invalid name (SQL injection attempt) must be rejected at startup. Tests in Tasks 9 and 11.
5. An `@Idempotent` handler invoked before the invoker is wired (e.g. an event published from a `@PostConstruct`) must fail loudly, not run without deduplication. Test in Task 3.

---

## File Structure

Core (`spring-boot-cqrs-core/src/main/java/com/borjaglez/cqrs/`):
- Create `idempotency/Idempotent.java` — method annotation.
- Create `idempotency/Acquisition.java` — `ACQUIRED` / `DUPLICATE`.
- Create `idempotency/IdempotencyStore.java` — SPI.
- Create `idempotency/Outcome.java` — invoker result.
- Create `idempotency/IdempotentInvoker.java` — acquire / run / complete-or-release.
- Create `idempotency/DuplicateMessageException.java`.
- Create `idempotency/InMemoryIdempotencyStore.java`.
- Create `idempotency/IdempotencyRegistrar.java` — wires invoker into registries, fails fast.
- Modify `event/registry/EventHandlerRegistry.java`, `command/registry/CommandHandlerRegistry.java`, `discovery/BeanPostProcessorHandlerDiscoverer.java`.

Starters (identical in `spring-boot-cqrs-boot3-starter` and `spring-boot-cqrs-boot4-starter`):
- Modify `autoconfigure/CqrsProperties.java` (add `idempotency`).
- Create `autoconfigure/CqrsIdempotencyAutoConfiguration.java`.
- Modify `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

Test support: modify `spring-boot-cqrs-test/.../annotation/CqrsTestConfiguration.java`.

New module `spring-boot-cqrs-jdbc` (`src/main/java/com/borjaglez/cqrs/jdbc/`):
- `JdbcIdempotencyStore.java`, `JdbcIdempotencyCleanup.java`, `JdbcIdempotencySchemaInitializer.java`, `JdbcCqrsProperties.java`, `CqrsJdbcIdempotencyAutoConfiguration.java`.
- `src/main/resources/com/borjaglez/cqrs/jdbc/schema-idempotency.sql`, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

RabbitMQ: `src/test/java/com/borjaglez/cqrs/rabbitmq/integration/RabbitMqIdempotencyIntegrationTest.java`.

Docs: create `docs/idempotency.md`; modify `docs/configuration.md`, `docs/middleware.md`, `README.md`.

Build: `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`.

---

### Task 1: Core idempotency SPI and invoker

**Files:**
- Create: `spring-boot-cqrs-core/src/main/java/com/borjaglez/cqrs/idempotency/{Idempotent,Acquisition,IdempotencyStore,Outcome,IdempotentInvoker,DuplicateMessageException}.java`
- Test: `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/idempotency/IdempotentInvokerTest.java`, `DuplicateMessageExceptionTest.java`

**Interfaces:**
- Produces:
  - `@Idempotent { String name() default ""; }` (METHOD, RUNTIME, `@Documented`)
  - `enum Acquisition { ACQUIRED, DUPLICATE }`
  - `interface IdempotencyStore { Acquisition tryAcquire(String handlerId, String messageId); void complete(String handlerId, String messageId); void release(String handlerId, String messageId); default <T> T runInScope(Supplier<T> work) }`
  - `record Outcome<T>(boolean duplicate, T result)` with `static <T> Outcome<T> skipped()` and `static <T> Outcome<T> applied(T result)`
  - `final class IdempotentInvoker { IdempotentInvoker(IdempotencyStore); <T> Outcome<T> invoke(String handlerId, String messageId, Supplier<T> effect) }`
  - `class DuplicateMessageException extends RuntimeException { DuplicateMessageException(String handlerId, String messageId); String getHandlerId(); String getMessageId() }`

- [ ] **Step 1: Write the failing tests**

`IdempotentInvokerTest.java`:

```java
package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class IdempotentInvokerTest {

  private IdempotencyStore store;
  private IdempotentInvoker invoker;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    store = mock(IdempotencyStore.class);
    when(store.runInScope(any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    invoker = new IdempotentInvoker(store);
  }

  @Test
  void runsTheEffectAndCompletesWhenAcquired() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.ACQUIRED);

    Outcome<String> outcome = invoker.invoke("h", "m", () -> "done");

    assertThat(outcome).isEqualTo(Outcome.applied("done"));
    assertThat(outcome.duplicate()).isFalse();
    InOrder order = inOrder(store);
    order.verify(store).tryAcquire("h", "m");
    order.verify(store).complete("h", "m");
    verify(store, never()).release("h", "m");
  }

  @Test
  void skipsTheEffectWhenDuplicate() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.DUPLICATE);
    AtomicInteger runs = new AtomicInteger();

    Outcome<Integer> outcome = invoker.invoke("h", "m", runs::incrementAndGet);

    assertThat(outcome).isEqualTo(Outcome.skipped());
    assertThat(outcome.duplicate()).isTrue();
    assertThat(outcome.result()).isNull();
    assertThat(runs).hasValue(0);
    verify(store, never()).complete("h", "m");
  }

  @Test
  void releasesAndRethrowsWhenTheEffectFails() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.ACQUIRED);
    IllegalStateException failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    verify(store).release("h", "m");
    verify(store, never()).complete("h", "m");
  }

  @Test
  void releasesAndRethrowsWhenTheEffectThrowsAnError() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.ACQUIRED);
    AssertionError failure = new AssertionError("boom");

    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    verify(store).release("h", "m");
  }

  @Test
  void runsEverythingInsideTheStoreScope() {
    IdempotencyStore scoped = mock(IdempotencyStore.class);
    when(scoped.runInScope(any())).thenReturn(Outcome.skipped());

    assertThat(new IdempotentInvoker(scoped).invoke("h", "m", () -> "x"))
        .isEqualTo(Outcome.skipped());
    verify(scoped, never()).tryAcquire("h", "m");
  }

  @Test
  void defaultScopeRunsTheWorkDirectly() {
    IdempotencyStore plain =
        new IdempotencyStore() {
          @Override
          public Acquisition tryAcquire(String handlerId, String messageId) {
            return Acquisition.ACQUIRED;
          }

          @Override
          public void complete(String handlerId, String messageId) {}

          @Override
          public void release(String handlerId, String messageId) {}
        };

    assertThat(plain.runInScope(() -> "work")).isEqualTo("work");
  }

  @Test
  void rejectsANullStore() {
    assertThatThrownBy(() -> new IdempotentInvoker(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("store");
  }
}
```

`DuplicateMessageExceptionTest.java`:

```java
package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DuplicateMessageExceptionTest {

  @Test
  void namesTheHandlerAndTheMessage() {
    DuplicateMessageException exception = new DuplicateMessageException("orders#handle", "id-1");

    assertThat(exception.getHandlerId()).isEqualTo("orders#handle");
    assertThat(exception.getMessageId()).isEqualTo("id-1");
    assertThat(exception)
        .hasMessage("Message id-1 was already processed by idempotent handler orders#handle");
  }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "com.borjaglez.cqrs.idempotency.*"`
Expected: compilation failure, `IdempotencyStore` / `IdempotentInvoker` not found.

- [ ] **Step 3: Implement**

`Idempotent.java`:

```java
package com.borjaglez.cqrs.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a {@code @HandleCommand} or {@code @HandleEvent} method run at most once per message: a
 * redelivered command or event whose previous processing by this handler succeeded is skipped. A
 * failed processing leaves no trace, so the redelivery runs the handler again. Requires an {@link
 * IdempotencyStore} bean.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

  /**
   * Stable id of the handler in the store. Defaults to {@code <beanName>#<methodName>}; set it so
   * that renaming the bean or the method does not make the handler process recent messages again.
   */
  String name() default "";
}
```

`Acquisition.java`:

```java
package com.borjaglez.cqrs.idempotency;

/** Result of {@link IdempotencyStore#tryAcquire(String, String)}. */
public enum Acquisition {
  /** The caller owns the message for this handler and must run the handler. */
  ACQUIRED,
  /** The handler already processed the message, or another delivery is processing it. */
  DUPLICATE
}
```

`IdempotencyStore.java`:

```java
package com.borjaglez.cqrs.idempotency;

import java.util.function.Supplier;

/**
 * Remembers which handler processed which message. {@link IdempotentInvoker} calls {@link
 * #tryAcquire}, runs the handler, then calls {@link #complete} or, when the handler failed, {@link
 * #release}; all of it inside {@link #runInScope}.
 */
public interface IdempotencyStore {

  Acquisition tryAcquire(String handlerId, String messageId);

  void complete(String handlerId, String messageId);

  void release(String handlerId, String messageId);

  /**
   * Runs {@code work}, which acquires, runs the handler and completes or releases. A transactional
   * store overrides it to run everything in one transaction, so the marker commits with the
   * handler's effect.
   */
  default <T> T runInScope(Supplier<T> work) {
    return work.get();
  }
}
```

`Outcome.java`:

```java
package com.borjaglez.cqrs.idempotency;

/**
 * Result of {@link IdempotentInvoker#invoke}: whether the message was a duplicate and, when it was
 * not, what the handler returned (possibly {@code null}).
 */
public record Outcome<T>(boolean duplicate, T result) {

  public static <T> Outcome<T> skipped() {
    return new Outcome<>(true, null);
  }

  public static <T> Outcome<T> applied(T result) {
    return new Outcome<>(false, result);
  }
}
```

`IdempotentInvoker.java`:

```java
package com.borjaglez.cqrs.idempotency;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runs an effect at most once per handler and message. The registries use it for {@link
 * Idempotent} handlers; applications can use it directly, for example in a plain listener.
 */
public final class IdempotentInvoker {

  private final IdempotencyStore store;

  public IdempotentInvoker(IdempotencyStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  public <T> Outcome<T> invoke(String handlerId, String messageId, Supplier<T> effect) {
    return store.runInScope(
        () -> {
          if (store.tryAcquire(handlerId, messageId) == Acquisition.DUPLICATE) {
            return Outcome.skipped();
          }
          T result;
          try {
            result = effect.get();
          } catch (RuntimeException | Error e) {
            store.release(handlerId, messageId);
            throw e;
          }
          store.complete(handlerId, messageId);
          return Outcome.applied(result);
        });
  }
}
```

`DuplicateMessageException.java`:

```java
package com.borjaglez.cqrs.idempotency;

import lombok.Getter;

/**
 * Thrown to the caller of {@code dispatchAndReceive} when an {@link Idempotent} command handler
 * that returns a result already processed the command: the result is not stored, so it cannot be
 * returned again.
 */
@Getter
public class DuplicateMessageException extends RuntimeException {

  private final String handlerId;
  private final String messageId;

  public DuplicateMessageException(String handlerId, String messageId) {
    super("Message " + messageId + " was already processed by idempotent handler " + handlerId);
    this.handlerId = handlerId;
    this.messageId = messageId;
  }
}
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "com.borjaglez.cqrs.idempotency.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-core/src
git commit -m "feat(core): add the idempotency store SPI and invoker"
```

---

### Task 2: In-memory idempotency store

**Files:**
- Create: `spring-boot-cqrs-core/src/main/java/com/borjaglez/cqrs/idempotency/InMemoryIdempotencyStore.java`
- Test: `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/idempotency/InMemoryIdempotencyStoreTest.java`

**Interfaces:**
- Consumes: `IdempotencyStore`, `Acquisition`, `IdempotentInvoker` (Task 1).
- Produces: `InMemoryIdempotencyStore(Duration retention, Duration lease)`, `InMemoryIdempotencyStore(Duration retention, Duration lease, Clock clock)`, package-private `int size()`, constant `static final int SWEEP_EVERY = 1_000`.

- [ ] **Step 1: Write the failing test**

```java
package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

  private static final Duration RETENTION = Duration.ofDays(7);
  private static final Duration LEASE = Duration.ofMinutes(5);

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T10:00:00Z"));
  private final InMemoryIdempotencyStore store =
      new InMemoryIdempotencyStore(RETENTION, LEASE, clock);

  @Test
  void firstAcquisitionWinsAndACompletedMessageIsADuplicate() {
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
    store.complete("h", "m");

    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.DUPLICATE);
    assertThat(store.tryAcquire("other", "m")).isEqualTo(Acquisition.ACQUIRED);
    assertThat(store.tryAcquire("h", "other")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void aReleasedMessageCanBeAcquiredAgain() {
    store.tryAcquire("h", "m");
    store.release("h", "m");

    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void anInProgressMessageIsADuplicateUntilItsLeaseExpires() {
    store.tryAcquire("h", "m");
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.DUPLICATE);

    clock.advance(LEASE);

    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void aCompletedMessageIsForgottenAfterTheRetention() {
    store.tryAcquire("h", "m");
    store.complete("h", "m");

    clock.advance(RETENTION.minusSeconds(1));
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.DUPLICATE);

    clock.advance(Duration.ofSeconds(1));
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void expiredEntriesAreSweptPeriodically() {
    for (int i = 0; i < 10; i++) {
      store.tryAcquire("h", "old-" + i);
      store.complete("h", "old-" + i);
    }
    clock.advance(RETENTION);
    int acquisitionsSoFar = 10;
    for (int i = acquisitionsSoFar; i < InMemoryIdempotencyStore.SWEEP_EVERY - 1; i++) {
      store.tryAcquire("h", "new-" + i);
    }
    assertThat(store.size()).isEqualTo(InMemoryIdempotencyStore.SWEEP_EVERY - 1);

    store.tryAcquire("h", "last");

    assertThat(store.size()).isEqualTo(InMemoryIdempotencyStore.SWEEP_EVERY - 10);
  }

  @Test
  void concurrentDeliveriesApplyTheEffectOnce() throws Exception {
    IdempotentInvoker invoker = new IdempotentInvoker(store);
    AtomicInteger applied = new AtomicInteger();
    int threads = 16;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return invoker.invoke("h", "m", applied::incrementAndGet);
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(applied).hasValue(1);
  }

  @Test
  void rejectsNonPositiveDurations() {
    assertThatThrownBy(() -> new InMemoryIdempotencyStore(Duration.ZERO, LEASE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("retention must be positive: PT0S");
    assertThatThrownBy(() -> new InMemoryIdempotencyStore(RETENTION, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lease must be positive: PT-1S");
    assertThatThrownBy(() -> new InMemoryIdempotencyStore(null, LEASE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("retention must be positive: null");
  }

  @Test
  void usesTheSystemClockByDefault() {
    InMemoryIdempotencyStore systemStore = new InMemoryIdempotencyStore(RETENTION, LEASE);

    assertThat(systemStore.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "InMemoryIdempotencyStoreTest"`
Expected: compilation failure, `InMemoryIdempotencyStore` not found.

- [ ] **Step 3: Implement**

```java
package com.borjaglez.cqrs.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link IdempotencyStore} kept in this process's memory. Suitable for tests and single-instance
 * applications: markers are not shared between instances and are lost on restart, and a crash
 * between the handler's effect and {@link #complete} lets a redelivery apply the effect again.
 *
 * <p>An acquired message blocks duplicates for {@code lease}; a completed one for {@code
 * retention}. Expired entries are removed when touched and by a sweep every {@value #SWEEP_EVERY}
 * acquisitions.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

  static final int SWEEP_EVERY = 1_000;

  private record Key(String handlerId, String messageId) {}

  private final ConcurrentHashMap<Key, Instant> expiries = new ConcurrentHashMap<>();
  private final AtomicInteger acquisitions = new AtomicInteger();
  private final Duration retention;
  private final Duration lease;
  private final Clock clock;

  public InMemoryIdempotencyStore(Duration retention, Duration lease) {
    this(retention, lease, Clock.systemUTC());
  }

  public InMemoryIdempotencyStore(Duration retention, Duration lease, Clock clock) {
    this.retention = positive("retention", retention);
    this.lease = positive("lease", lease);
    this.clock = clock;
  }

  @Override
  public Acquisition tryAcquire(String handlerId, String messageId) {
    sweepPeriodically();
    Instant now = clock.instant();
    boolean[] acquired = {false};
    expiries.compute(
        new Key(handlerId, messageId),
        (key, expiry) -> {
          if (expiry != null && expiry.isAfter(now)) {
            return expiry;
          }
          acquired[0] = true;
          return now.plus(lease);
        });
    return acquired[0] ? Acquisition.ACQUIRED : Acquisition.DUPLICATE;
  }

  @Override
  public void complete(String handlerId, String messageId) {
    expiries.put(new Key(handlerId, messageId), clock.instant().plus(retention));
  }

  @Override
  public void release(String handlerId, String messageId) {
    expiries.remove(new Key(handlerId, messageId));
  }

  int size() {
    return expiries.size();
  }

  private void sweepPeriodically() {
    if (acquisitions.incrementAndGet() % SWEEP_EVERY == 0) {
      Instant now = clock.instant();
      expiries.values().removeIf(expiry -> !expiry.isAfter(now));
    }
  }

  private static Duration positive(String name, Duration value) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(name + " must be positive: " + value);
    }
    return value;
  }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "InMemoryIdempotencyStoreTest"`
Expected: PASS. In `expiredEntriesAreSweptPeriodically` the 1,000th call sweeps the ten expired entries before adding `last`, leaving 990.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-core/src
git commit -m "feat(core): add an in-memory idempotency store"
```

---

### Task 3: Idempotent event handlers in `EventHandlerRegistry` (regression test)

**Files:**
- Modify: `spring-boot-cqrs-core/src/main/java/com/borjaglez/cqrs/event/registry/EventHandlerRegistry.java`
- Create: `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/fixtures/IdempotentEventHandlers.java`
- Test: `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/event/registry/EventHandlerRegistryTest.java` (add tests)

**Interfaces:**
- Consumes: `IdempotentInvoker`, `Outcome`, `InMemoryIdempotencyStore`.
- Produces:
  - `HandlerInfo` gains a last component `String idempotencyKey` (nullable) and `boolean idempotent()`; a new constructor with the previous canonical signature `(Object, MethodHandle, String, EventHandlerCondition, boolean)` delegates with `null`.
  - `void register(Class<?> eventClass, Object bean, Method method, String messageName, Expression condition, BeanResolver beanResolver, boolean remote, String idempotencyKey)`; the existing 7-argument overload delegates with `null`.
  - `void setIdempotentInvoker(IdempotentInvoker invoker)`.

- [ ] **Step 1: Write the fixture and the failing regression test**

`IdempotentEventHandlers.java`:

```java
package com.borjaglez.cqrs.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

/** Two handlers of the same event; {@code second} fails the first {@code failuresLeft} times. */
public class IdempotentEventHandlers {

  public final AtomicInteger firstCalls = new AtomicInteger();
  public final AtomicInteger secondCalls = new AtomicInteger();
  public final AtomicInteger failuresLeft = new AtomicInteger();

  public void first(TestEvent event) {
    firstCalls.incrementAndGet();
  }

  public void second(TestEvent event) {
    if (failuresLeft.getAndDecrement() > 0) {
      throw new IllegalStateException("second failed");
    }
    secondCalls.incrementAndGet();
  }
}
```

Add to `EventHandlerRegistryTest`. New imports: `java.lang.invoke.MethodHandles`, `java.time.Duration`, `static org.assertj.core.groups.Tuple.tuple`, `com.borjaglez.cqrs.fixtures.IdempotentEventHandlers`, `com.borjaglez.cqrs.idempotency.Acquisition`, `com.borjaglez.cqrs.idempotency.IdempotentInvoker`, `com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore` (`Expression`, `SpelExpressionParser`, `Level`, `Method` are already imported). Before writing the condition test, read the existing condition tests in this class and use the same SpEL syntax they use to reference the event (`data == 'other'` below assumes the event is the root object).

```java
  private IdempotentEventHandlers registerIdempotentHandlers() throws Exception {
    IdempotentEventHandlers handlers = new IdempotentEventHandlers();
    registry.register(
        TestEvent.class,
        handlers,
        IdempotentEventHandlers.class.getMethod("first", TestEvent.class),
        "test.event",
        null,
        null,
        true,
        "projector#first");
    registry.register(
        TestEvent.class,
        handlers,
        IdempotentEventHandlers.class.getMethod("second", TestEvent.class),
        "test.event",
        null,
        null,
        true,
        "projector#second");
    registry.setIdempotentInvoker(
        new IdempotentInvoker(
            new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5))));
    return handlers;
  }

  @Test
  void redeliveredEventDoesNotReapplyHandlersThatAlreadySucceeded() throws Exception {
    IdempotentEventHandlers handlers = registerIdempotentHandlers();
    handlers.failuresLeft.set(1);
    TestEvent event = new TestEvent("data");

    assertThatThrownBy(() -> registry.handleRemote(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("second failed");
    registry.handleRemote(event); // redelivery with the same eventId

    assertThat(handlers.firstCalls).hasValue(1);
    assertThat(handlers.secondCalls).hasValue(1);
  }

  @Test
  void sameEventTwiceIsAppliedOncePerHandler() throws Exception {
    IdempotentEventHandlers handlers = registerIdempotentHandlers();
    TestEvent event = new TestEvent("data");

    registry.handle(event);
    registry.handle(event);
    registry.handle(new TestEvent("other"));

    assertThat(handlers.firstCalls).hasValue(2);
    assertThat(handlers.secondCalls).hasValue(2);
    assertThat(logs.list)
        .anyMatch(
            e ->
                e.getLevel() == Level.DEBUG
                    && e.getFormattedMessage()
                        .equals(
                            "Skipping event "
                                + event.getEventId()
                                + " already processed by idempotent handler projector#first"));
  }

  @Test
  void idempotentHandlerSkippedByItsConditionLeavesNoMarker() throws Exception {
    IdempotentEventHandlers handlers = new IdempotentEventHandlers();
    Expression onlyOther = new SpelExpressionParser().parseExpression("data == 'other'");
    registry.register(
        TestEvent.class,
        handlers,
        IdempotentEventHandlers.class.getMethod("first", TestEvent.class),
        "test.event",
        onlyOther,
        null,
        true,
        "projector#first");
    InMemoryIdempotencyStore store =
        new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5));
    registry.setIdempotentInvoker(new IdempotentInvoker(store));

    TestEvent event = new TestEvent("data");

    registry.handle(event);

    assertThat(handlers.firstCalls).hasValue(0);
    assertThat(store.tryAcquire("projector#first", event.getEventId()))
        .isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void idempotentHandlerWithoutInvokerFailsLoudly() throws Exception {
    IdempotentEventHandlers handlers = new IdempotentEventHandlers();
    registry.register(
        TestEvent.class,
        handlers,
        IdempotentEventHandlers.class.getMethod("first", TestEvent.class),
        "test.event",
        null,
        null,
        true,
        "projector#first");

    assertThatThrownBy(() -> registry.handle(new TestEvent("data")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Handler projector#first is @Idempotent but no IdempotencyStore is configured yet;"
                + " add spring-boot-cqrs-jdbc with a DataSource, set"
                + " cqrs.idempotency.store=in-memory, or define an IdempotencyStore bean");
    assertThat(handlers.firstCalls).hasValue(0);
  }

  @Test
  void handlerInfoKeepsTheIdempotencyKey() throws Exception {
    registerIdempotentHandlers();
    registry.register(
        TestEvent.class,
        new TestEventHandler(),
        TestEventHandler.class.getMethod("handle", TestEvent.class),
        "test.event");

    assertThat(registry.getHandlerInfos(TestEvent.class))
        .extracting(
            EventHandlerRegistry.HandlerInfo::idempotencyKey,
            EventHandlerRegistry.HandlerInfo::idempotent)
        .containsExactly(
            tuple("projector#first", true),
            tuple("projector#second", true),
            tuple(null, false));
  }

  @Test
  void handlerInfoWithThePreviousCanonicalSignatureIsNotIdempotent() {
    EventHandlerRegistry.HandlerInfo info =
        new EventHandlerRegistry.HandlerInfo(
            new Object(), MethodHandles.constant(String.class, "x"), "name", null, false);

    assertThat(info.idempotencyKey()).isNull();
    assertThat(info.idempotent()).isFalse();
  }

  @Test
  void checkedFailureOfAnIdempotentHandlerIsWrappedAndReleased() throws Exception {
    Method method = CheckedThrowingEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(
        TestEvent.class,
        new CheckedThrowingEventHandler(),
        method,
        "test.event",
        null,
        null,
        true,
        "checked#handle");
    InMemoryIdempotencyStore store =
        new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5));
    registry.setIdempotentInvoker(new IdempotentInvoker(store));
    TestEvent event = new TestEvent("data");

    assertThatThrownBy(() -> registry.handle(event))
        .isInstanceOf(EventHandlerExecutionException.class);
    assertThat(store.tryAcquire("checked#handle", event.getEventId()))
        .isEqualTo(Acquisition.ACQUIRED);
  }
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "EventHandlerRegistryTest"`
Expected: compilation failure, the 8-argument `register`, `setIdempotentInvoker` and `HandlerInfo.idempotencyKey()` do not exist. This is the regression test of the issue: without the change there is no way to keep `first` from running twice.

- [ ] **Step 3: Implement**

In `EventHandlerRegistry`:

1. `HandlerInfo`: add the component and the compatibility constructor.

```java
  public record HandlerInfo(
      Object bean,
      MethodHandle handle,
      String messageName,
      EventHandlerCondition condition,
      boolean remote,
      String idempotencyKey) {

    /** Creates the information of a remote handler without condition. */
    public HandlerInfo(Object bean, MethodHandle handle, String messageName) {
      this(bean, handle, messageName, null);
    }

    /** Creates the information of a remote handler. */
    public HandlerInfo(
        Object bean, MethodHandle handle, String messageName, EventHandlerCondition condition) {
      this(bean, handle, messageName, condition, true);
    }

    /** Creates the information of a handler that is not idempotent. */
    public HandlerInfo(
        Object bean,
        MethodHandle handle,
        String messageName,
        EventHandlerCondition condition,
        boolean remote) {
      this(bean, handle, messageName, condition, remote, null);
    }

    /** Whether the handler is {@code @Idempotent}. */
    public boolean idempotent() {
      return idempotencyKey != null;
    }
  }
```

Add `@param idempotencyKey the handler id in the idempotency store, or {@code null} when the handler is not {@code @Idempotent}` to the record Javadoc.

2. Field and setter:

```java
  private volatile IdempotentInvoker idempotentInvoker;

  /** Sets the invoker that deduplicates {@code @Idempotent} handlers. */
  public void setIdempotentInvoker(IdempotentInvoker idempotentInvoker) {
    this.idempotentInvoker = idempotentInvoker;
  }
```

3. The current 7-argument `register` becomes a delegate, and the body moves to the new overload:

```java
  public void register(
      Class<?> eventClass,
      Object bean,
      Method method,
      String messageName,
      Expression condition,
      BeanResolver beanResolver,
      boolean remote) {
    register(eventClass, bean, method, messageName, condition, beanResolver, remote, null);
  }

  /**
   * Registers an event handler.
   *
   * @param idempotencyKey the handler id in the idempotency store, or {@code null} when the
   *     handler is not {@code @Idempotent}
   */
  public void register(
      Class<?> eventClass,
      Object bean,
      Method method,
      String messageName,
      Expression condition,
      BeanResolver beanResolver,
      boolean remote,
      String idempotencyKey) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    EventHandlerCondition handlerCondition =
        condition == null
            ? null
            : new EventHandlerCondition(condition, beanResolver, method.toGenericString());
    HandlerInfo info =
        new HandlerInfo(bean, handle, messageName, handlerCondition, remote, idempotencyKey);
    handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(info);
    messageNames.add(messageName, eventClass);
  }
```

4. The loop and its helpers:

```java
    for (HandlerInfo info : handlerList) {
      if (remoteOnly && !info.remote()) {
        continue;
      }
      if (info.condition() != null && !info.condition().matches(event)) {
        continue;
      }
      if (!info.idempotent()) {
        invoke(info, event);
      } else if (invoker(info)
          .invoke(info.idempotencyKey(), event.getEventId(), () -> invoke(info, event))
          .duplicate()) {
        LOG.debug(
            "Skipping event "
                + event.getEventId()
                + " already processed by idempotent handler "
                + info.idempotencyKey());
      }
    }
  }

  private static Object invoke(HandlerInfo info, Event event) {
    try {
      info.handle().invoke(info.bean(), event);
      return null;
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new EventHandlerExecutionException(
          "Failed to handle event " + event.getClass().getName(), e);
    }
  }

  private IdempotentInvoker invoker(HandlerInfo info) {
    IdempotentInvoker invoker = idempotentInvoker;
    if (invoker == null) {
      throw new IllegalStateException(
          "Handler "
              + info.idempotencyKey()
              + " is @Idempotent but no IdempotencyStore is configured yet; add"
              + " spring-boot-cqrs-jdbc with a DataSource, set cqrs.idempotency.store=in-memory,"
              + " or define an IdempotencyStore bean");
    }
    return invoker;
  }
```

Note: `catch (Throwable e)` keeps wrapping `Error`s like today; `IdempotentInvoker` sees only `RuntimeException`s from `invoke`. The existing behaviour for non-idempotent handlers is unchanged.

- [ ] **Step 4: Run the core tests and verify they pass**

Run: `./gradlew :spring-boot-cqrs-core:test`
Expected: PASS, including every existing registry test.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-core/src
git commit -m "feat(core): skip idempotent event handlers that already processed an event"
```

---

### Task 4: Idempotent command handlers in `CommandHandlerRegistry`

**Files:**
- Modify: `spring-boot-cqrs-core/src/main/java/com/borjaglez/cqrs/command/registry/CommandHandlerRegistry.java`
- Test: `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/command/registry/CommandHandlerRegistryTest.java` (add tests)

**Interfaces:**
- Consumes: `IdempotentInvoker`, `Outcome`, `DuplicateMessageException`, `InMemoryIdempotencyStore`.
- Produces:
  - `HandlerInfo` gains last component `String idempotencyKey` and `boolean idempotent()`; compatibility constructor with the previous canonical signature `(Object, MethodHandle, String, boolean, boolean)`.
  - `void register(Class<?> commandClass, Object bean, Method method, String messageName, boolean requiresValidation, boolean remote, String idempotencyKey)`; the 6-argument overload delegates with `null`.
  - `void setIdempotentInvoker(IdempotentInvoker invoker)`.

- [ ] **Step 1: Write the failing tests**

Read `TestCommandHandler` and `TestReturningCommandHandler` in `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/fixtures/` first: use a `void` handler method and a method that returns a value, and count calls through a field or a Mockito `spy`. If neither fixture counts calls, create `fixtures/CountingCommandHandlers.java`:

```java
package com.borjaglez.cqrs.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

public class CountingCommandHandlers {

  public final AtomicInteger calls = new AtomicInteger();

  public void handle(TestCommand command) {
    calls.incrementAndGet();
  }

  public String handleAndReturn(TestCommand command) {
    return "result-" + calls.incrementAndGet();
  }
}
```

Tests to add (imports: `java.time.Duration`, `java.lang.invoke.MethodHandles`, `com.borjaglez.cqrs.idempotency.*` classes used):

```java
  private IdempotentInvoker invoker() {
    return new IdempotentInvoker(
        new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));
  }

  @Test
  void duplicateVoidCommandIsSkipped() throws Exception {
    CountingCommandHandlers handlers = new CountingCommandHandlers();
    registry.register(
        TestCommand.class,
        handlers,
        CountingCommandHandlers.class.getMethod("handle", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handle");
    registry.setIdempotentInvoker(invoker());
    TestCommand command = new TestCommand("data");

    assertThat(registry.handle(command)).isNull();
    assertThat(registry.handle(command)).isNull();

    assertThat(handlers.calls).hasValue(1);
  }

  @Test
  void duplicateCommandWithAResultThrows() throws Exception {
    CountingCommandHandlers handlers = new CountingCommandHandlers();
    registry.register(
        TestCommand.class,
        handlers,
        CountingCommandHandlers.class.getMethod("handleAndReturn", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handleAndReturn");
    registry.setIdempotentInvoker(invoker());
    TestCommand command = new TestCommand("data");

    assertThat(registry.handle(command)).isEqualTo("result-1");
    assertThatThrownBy(() -> registry.handle(command))
        .isInstanceOf(DuplicateMessageException.class)
        .hasMessage(
            "Message "
                + command.getCommandId()
                + " was already processed by idempotent handler orders#handleAndReturn");
    assertThat(handlers.calls).hasValue(1);
  }

  @Test
  void failedIdempotentCommandRunsAgain() throws Exception {
    Method method = ThrowingCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(
        TestCommand.class, new ThrowingCommandHandler(), method, "test.command", false, true,
        "throwing#handle");
    registry.setIdempotentInvoker(invoker());
    TestCommand command = new TestCommand("data");

    assertThatThrownBy(() -> registry.handle(command)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> registry.handle(command))
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(DuplicateMessageException.class);
  }

  @Test
  void checkedFailureOfAnIdempotentCommandIsWrapped() throws Exception {
    Method method = CheckedThrowingCommandHandler.class.getMethod("handle", TestCommand.class);
    registry.register(
        TestCommand.class, new CheckedThrowingCommandHandler(), method, "test.command", false,
        true, "checked#handle");
    registry.setIdempotentInvoker(invoker());

    assertThatThrownBy(() -> registry.handle(new TestCommand("data")))
        .isInstanceOf(CommandHandlerExecutionException.class);
  }

  @Test
  void idempotentCommandWithoutInvokerFailsLoudly() throws Exception {
    CountingCommandHandlers handlers = new CountingCommandHandlers();
    registry.register(
        TestCommand.class,
        handlers,
        CountingCommandHandlers.class.getMethod("handle", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handle");

    assertThatThrownBy(() -> registry.handle(new TestCommand("data")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Handler orders#handle is @Idempotent but no IdempotencyStore");
    assertThat(handlers.calls).hasValue(0);
  }

  @Test
  void handlerInfoWithThePreviousCanonicalSignatureIsNotIdempotent() {
    CommandHandlerRegistry.HandlerInfo info =
        new CommandHandlerRegistry.HandlerInfo(
            new Object(), MethodHandles.constant(String.class, "x"), "name", false, true);

    assertThat(info.idempotencyKey()).isNull();
    assertThat(info.idempotent()).isFalse();
  }
```

Check that `ThrowingCommandHandler` and `CheckedThrowingCommandHandler` have a `handle(TestCommand)` method; adapt the method name to what the fixtures declare.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "CommandHandlerRegistryTest"`
Expected: compilation failure (7-argument `register`, `setIdempotentInvoker`, `idempotencyKey()` missing).

- [ ] **Step 3: Implement**

```java
  public record HandlerInfo(
      Object bean,
      MethodHandle handle,
      String messageName,
      boolean requiresValidation,
      boolean remote,
      String idempotencyKey) {

    /** Creates the information of a remote handler. */
    public HandlerInfo(
        Object bean, MethodHandle handle, String messageName, boolean requiresValidation) {
      this(bean, handle, messageName, requiresValidation, true);
    }

    /** Creates the information of a handler that is not idempotent. */
    public HandlerInfo(
        Object bean,
        MethodHandle handle,
        String messageName,
        boolean requiresValidation,
        boolean remote) {
      this(bean, handle, messageName, requiresValidation, remote, null);
    }

    /** Whether the handler is {@code @Idempotent}. */
    public boolean idempotent() {
      return idempotencyKey != null;
    }
  }

  private volatile IdempotentInvoker idempotentInvoker;

  /** Sets the invoker that deduplicates {@code @Idempotent} handlers. */
  public void setIdempotentInvoker(IdempotentInvoker idempotentInvoker) {
    this.idempotentInvoker = idempotentInvoker;
  }

  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation,
      boolean remote) {
    register(commandClass, bean, method, messageName, requiresValidation, remote, null);
  }

  /**
   * Registers the handler of a command.
   *
   * @param idempotencyKey the handler id in the idempotency store, or {@code null} when the
   *     handler is not {@code @Idempotent}
   */
  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation,
      boolean remote,
      String idempotencyKey) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info =
        new HandlerInfo(bean, handle, messageName, requiresValidation, remote, idempotencyKey);
    HandlerInfo existing = handlers.putIfAbsent(commandClass, info);
    if (existing != null) {
      throw new CommandAlreadyRegisteredException(commandClass);
    }
    messageNames.add(messageName, commandClass);
  }

  public Object handle(Command command) {
    HandlerInfo info = handlers.get(command.getClass());
    if (info == null) {
      throw notRegistered(command.getClass());
    }
    if (!info.idempotent()) {
      return invoke(info, command);
    }
    Outcome<Object> outcome =
        invoker(info)
            .invoke(info.idempotencyKey(), command.getCommandId(), () -> invoke(info, command));
    if (!outcome.duplicate()) {
      return outcome.result();
    }
    if (info.handle().type().returnType() == void.class) {
      return null;
    }
    throw new DuplicateMessageException(info.idempotencyKey(), command.getCommandId());
  }

  private static Object invoke(HandlerInfo info, Command command) {
    try {
      return info.handle().invoke(info.bean(), command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  private IdempotentInvoker invoker(HandlerInfo info) {
    IdempotentInvoker invoker = idempotentInvoker;
    if (invoker == null) {
      throw new IllegalStateException(
          "Handler "
              + info.idempotencyKey()
              + " is @Idempotent but no IdempotencyStore is configured yet; add"
              + " spring-boot-cqrs-jdbc with a DataSource, set cqrs.idempotency.store=in-memory,"
              + " or define an IdempotencyStore bean");
    }
    return invoker;
  }
```

Remove the now-replaced old 6-argument body. Update the record Javadoc with `@param idempotencyKey`.

- [ ] **Step 4: Run the core tests and verify they pass**

Run: `./gradlew :spring-boot-cqrs-core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-core/src
git commit -m "feat(core): skip idempotent command handlers that already processed a command"
```

---

### Task 5: Discover `@Idempotent` on handler methods

**Files:**
- Modify: `spring-boot-cqrs-core/src/main/java/com/borjaglez/cqrs/discovery/BeanPostProcessorHandlerDiscoverer.java`
- Create fixtures in `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/fixtures/`: `IdempotentCommandHandler.java`, `IdempotentEventHandler.java`, `IdempotentQueryHandler.java`, `IdempotentWithoutHandleAnnotation.java`
- Test: `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/discovery/BeanPostProcessorHandlerDiscovererTest.java` (add tests)

**Interfaces:**
- Consumes: `@Idempotent` (Task 1), the 8-argument `EventHandlerRegistry.register` (Task 3), the 7-argument `CommandHandlerRegistry.register` (Task 4).
- Produces: handler id `beanName + "#" + method.getName()` or `@Idempotent.name()`.

- [ ] **Step 1: Write fixtures and failing tests**

`fixtures/IdempotentCommandHandler.java`:

```java
package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.idempotency.Idempotent;

@CommandHandler
public class IdempotentCommandHandler {

  @HandleCommand
  @Idempotent
  public void handle(TestCommand command) {}
}
```

`fixtures/IdempotentEventHandler.java`:

```java
package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;

@EventHandler
public class IdempotentEventHandler {

  @HandleEvent
  @Idempotent(name = "stock-projector")
  public void on(TestEvent event) {}

  @HandleEvent
  public void notIdempotent(TestEvent event) {}
}
```

`fixtures/IdempotentQueryHandler.java`:

```java
package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class IdempotentQueryHandler {

  @HandleQuery
  @Idempotent
  public String handle(TestQuery query) {
    return "x";
  }
}
```

`fixtures/IdempotentWithoutHandleAnnotation.java`:

```java
package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.idempotency.Idempotent;

@EventHandler
public class IdempotentWithoutHandleAnnotation {

  @Idempotent
  public void on(TestEvent event) {}
}
```

Check `TestQuery`'s handler return type in `TestQueryHandler` and match it. Read the existing `BeanPostProcessorHandlerDiscovererTest` setup (how it builds the discoverer and registries) and add:

```java
  @Test
  void idempotentCommandHandlerGetsTheDefaultHandlerId() {
    discoverer.discover(new IdempotentCommandHandler(), "ordersHandler");

    assertThat(commandRegistry.getHandlerInfo(TestCommand.class))
        .get()
        .extracting(CommandHandlerRegistry.HandlerInfo::idempotencyKey)
        .isEqualTo("ordersHandler#handle");
  }

  @Test
  void idempotentEventHandlerUsesTheExplicitName() {
    discoverer.discover(new IdempotentEventHandler(), "projector");

    assertThat(eventRegistry.getHandlerInfos(TestEvent.class))
        .extracting(EventHandlerRegistry.HandlerInfo::idempotencyKey)
        .containsExactlyInAnyOrder("stock-projector", null);
  }

  @Test
  void idempotentQueryHandlerIsRejected() {
    assertThatThrownBy(() -> discoverer.discover(new IdempotentQueryHandler(), "queries"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("on bean 'queries' is a query handler")
        .hasMessageContaining("@Idempotent applies to command and event handlers only");
  }

  @Test
  void idempotentMethodWithoutHandlerAnnotationIsRejected() {
    assertThatThrownBy(
            () -> discoverer.discover(new IdempotentWithoutHandleAnnotation(), "projector"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("on bean 'projector' is annotated with @Idempotent")
        .hasMessageContaining("but not with @HandleCommand or @HandleEvent");
  }
```

Use the existing field names of the test class for the registries and discoverer.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "BeanPostProcessorHandlerDiscovererTest"`
Expected: FAIL — keys are `null`, and the query and missing-annotation cases do not throw.

- [ ] **Step 3: Implement**

In `discover`, after the three existing blocks:

```java
    if (commandHandler != null || eventHandler != null || queryHandler != null) {
      ReflectionUtils.doWithMethods(
          targetClass,
          method -> {
            throw new IllegalStateException(
                "Method "
                    + method.toGenericString()
                    + " on bean '"
                    + beanName
                    + "' is annotated with @Idempotent but not with @HandleCommand or"
                    + " @HandleEvent");
          },
          method ->
              method.isAnnotationPresent(Idempotent.class)
                  && !method.isAnnotationPresent(HandleCommand.class)
                  && !method.isAnnotationPresent(HandleEvent.class)
                  && !method.isAnnotationPresent(HandleQuery.class));
    }
```

`registerCommandHandler`: pass `idempotencyKey(beanName, method)` as the new last argument:

```java
    commandHandlerRegistry.register(
        commandClass,
        bean,
        invocable,
        messageName,
        requiresValidation,
        remote,
        idempotencyKey(beanName, method));
```

`registerEventHandler`:

```java
    eventHandlerRegistry.register(
        eventClass,
        bean,
        invocable,
        messageName,
        condition,
        beanResolver,
        remote,
        idempotencyKey(beanName, method));
```

`registerQueryHandler`, first statement:

```java
    if (method.isAnnotationPresent(Idempotent.class)) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " on bean '"
              + beanName
              + "' is a query handler; @Idempotent applies to command and event handlers only");
    }
```

New helper:

```java
  /** The handler id of an {@code @Idempotent} method, or {@code null} when it is not one. */
  private static String idempotencyKey(String beanName, Method method) {
    Idempotent idempotent = method.getAnnotation(Idempotent.class);
    if (idempotent == null) {
      return null;
    }
    return StringUtils.hasText(idempotent.name())
        ? idempotent.name()
        : beanName + "#" + method.getName();
  }
```

Import `com.borjaglez.cqrs.idempotency.Idempotent`. `method` is the method declared on the target class, so annotations are read from it, not from the proxy.

- [ ] **Step 4: Run the core tests and coverage**

Run: `./gradlew :spring-boot-cqrs-core:check`
Expected: PASS including `jacocoTestCoverageVerification`.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-core/src
git commit -m "feat(core): register handlers annotated with @Idempotent"
```

---

### Task 6: `IdempotencyRegistrar`

**Files:**
- Create: `spring-boot-cqrs-core/src/main/java/com/borjaglez/cqrs/idempotency/IdempotencyRegistrar.java`
- Test: `spring-boot-cqrs-core/src/test/java/com/borjaglez/cqrs/idempotency/IdempotencyRegistrarTest.java`

**Interfaces:**
- Consumes: `CommandHandlerRegistry.setIdempotentInvoker`, `EventHandlerRegistry.setIdempotentInvoker`, `HandlerInfo.idempotencyKey()` of both registries.
- Produces: `IdempotencyRegistrar implements SmartInitializingSingleton` with constructors `(CommandHandlerRegistry, EventHandlerRegistry, IdempotentInvoker /* nullable */)` and `(CommandHandlerRegistry, EventHandlerRegistry, Supplier<IdempotentInvoker> /* may supply null */)`. The supplier is called only in `afterSingletonsInstantiated`, so the starter can pass `ObjectProvider::getIfAvailable` without creating the store (and its `DataSource`) early.

- [ ] **Step 1: Write the failing test**

```java
package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.IdempotentEventHandlers;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.fixtures.TestEvent;

class IdempotencyRegistrarTest {

  private final CommandHandlerRegistry commands = new CommandHandlerRegistry();
  private final EventHandlerRegistry events = new EventHandlerRegistry();

  private void registerIdempotentEventHandler() throws Exception {
    events.register(
        TestEvent.class,
        new IdempotentEventHandlers(),
        IdempotentEventHandlers.class.getMethod("first", TestEvent.class),
        "test.event",
        null,
        null,
        true,
        "projector#first");
  }

  private void registerIdempotentCommandHandler() throws Exception {
    commands.register(
        TestCommand.class,
        new TestCommandHandler(),
        TestCommandHandler.class.getMethod("handle", TestCommand.class),
        "test.command",
        false,
        true,
        "orders#handle");
  }

  @Test
  void setsTheInvokerOnBothRegistries() throws Exception {
    registerIdempotentEventHandler();
    IdempotentEventHandlers handlers =
        (IdempotentEventHandlers) events.getHandlerInfos(TestEvent.class).get(0).bean();
    IdempotentInvoker invoker =
        new IdempotentInvoker(
            new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));

    new IdempotencyRegistrar(commands, events, invoker).afterSingletonsInstantiated();
    TestEvent event = new TestEvent("data");
    events.handle(event);
    events.handle(event);

    assertThat(handlers.firstCalls).hasValue(1);
  }

  @Test
  void setsTheInvokerOnTheCommandRegistry() throws Exception {
    registerIdempotentCommandHandler();
    IdempotentInvoker invoker =
        new IdempotentInvoker(
            new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));

    new IdempotencyRegistrar(commands, events, invoker).afterSingletonsInstantiated();

    assertThatCode(() -> commands.handle(new TestCommand("data"))).doesNotThrowAnyException();
  }

  @Test
  void failsWhenIdempotentHandlersHaveNoStore() throws Exception {
    registerIdempotentEventHandler();
    registerIdempotentCommandHandler();

    assertThatThrownBy(
            () ->
                new IdempotencyRegistrar(commands, events, (IdempotentInvoker) null)
                    .afterSingletonsInstantiated())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Handlers [orders#handle, projector#first] are annotated with @Idempotent but no"
                + " IdempotencyStore bean is configured; add spring-boot-cqrs-jdbc with a"
                + " DataSource, set cqrs.idempotency.store=in-memory, or define an"
                + " IdempotencyStore bean");
  }

  @Test
  void acceptsMissingStoreWhenNoHandlerIsIdempotent() throws Exception {
    commands.register(
        TestCommand.class,
        new TestCommandHandler(),
        TestCommandHandler.class.getMethod("handle", TestCommand.class),
        "test.command",
        false);
    events.register(
        TestEvent.class,
        new IdempotentEventHandlers(),
        IdempotentEventHandlers.class.getMethod("first", TestEvent.class),
        "test.event");

    assertThatCode(
            () ->
                new IdempotencyRegistrar(commands, events, (IdempotentInvoker) null)
                    .afterSingletonsInstantiated())
        .doesNotThrowAnyException();
  }

  @Test
  void resolvesTheInvokerLazily() throws Exception {
    registerIdempotentEventHandler();
    AtomicBoolean asked = new AtomicBoolean();
    IdempotencyRegistrar registrar =
        new IdempotencyRegistrar(
            commands,
            events,
            () -> {
              asked.set(true);
              return new IdempotentInvoker(
                  new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5)));
            });
    assertThat(asked).isFalse();

    registrar.afterSingletonsInstantiated();

    assertThat(asked).isTrue();
  }
}
```

Use the real handler method name of `TestCommandHandler` (read the fixture).

- [ ] **Step 2: Run and verify it fails**

Run: `./gradlew :spring-boot-cqrs-core:test --tests "IdempotencyRegistrarTest"`
Expected: compilation failure, `IdempotencyRegistrar` not found.

- [ ] **Step 3: Implement**

```java
package com.borjaglez.cqrs.idempotency;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.beans.factory.SmartInitializingSingleton;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;

/**
 * Hands the {@link IdempotentInvoker} to the registries once every singleton exists, before any
 * listener container starts. The registries are created before the beans a store needs (such as a
 * {@code DataSource}) because the handler discoverer is a {@code BeanPostProcessor}, so they cannot
 * receive it in their constructor. Fails startup when a handler is {@link Idempotent} and there is
 * no invoker.
 */
public class IdempotencyRegistrar implements SmartInitializingSingleton {

  private final CommandHandlerRegistry commandHandlerRegistry;
  private final EventHandlerRegistry eventHandlerRegistry;
  private final Supplier<IdempotentInvoker> invoker;

  /**
   * @param invoker the invoker, or {@code null} when no {@link IdempotencyStore} is configured
   */
  public IdempotencyRegistrar(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      IdempotentInvoker invoker) {
    this(commandHandlerRegistry, eventHandlerRegistry, () -> invoker);
  }

  /**
   * @param invoker supplies the invoker, or {@code null} when no {@link IdempotencyStore} is
   *     configured; called once all singletons exist
   */
  public IdempotencyRegistrar(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      Supplier<IdempotentInvoker> invoker) {
    this.commandHandlerRegistry = Objects.requireNonNull(commandHandlerRegistry);
    this.eventHandlerRegistry = Objects.requireNonNull(eventHandlerRegistry);
    this.invoker = Objects.requireNonNull(invoker);
  }

  @Override
  public void afterSingletonsInstantiated() {
    IdempotentInvoker invoker = this.invoker.get();
    if (invoker != null) {
      commandHandlerRegistry.setIdempotentInvoker(invoker);
      eventHandlerRegistry.setIdempotentInvoker(invoker);
      return;
    }
    List<String> idempotentHandlers = idempotentHandlers();
    if (!idempotentHandlers.isEmpty()) {
      throw new IllegalStateException(
          "Handlers "
              + idempotentHandlers
              + " are annotated with @Idempotent but no IdempotencyStore bean is configured; add"
              + " spring-boot-cqrs-jdbc with a DataSource, set cqrs.idempotency.store=in-memory,"
              + " or define an IdempotencyStore bean");
    }
  }

  private List<String> idempotentHandlers() {
    List<String> keys = new ArrayList<>();
    for (Class<?> command : commandHandlerRegistry.getRegisteredCommands()) {
      commandHandlerRegistry
          .getHandlerInfo(command)
          .map(CommandHandlerRegistry.HandlerInfo::idempotencyKey)
          .ifPresent(keys::add);
    }
    for (Class<?> event : eventHandlerRegistry.getRegisteredEvents()) {
      for (EventHandlerRegistry.HandlerInfo info : eventHandlerRegistry.getHandlerInfos(event)) {
        if (info.idempotent()) {
          keys.add(info.idempotencyKey());
        }
      }
    }
    keys.sort(null);
    return keys;
  }
}
```

`Optional.map` of a `null` key yields empty, so non-idempotent command handlers are skipped. `getHandlerInfo` always returns a value for a registered command; the `ifPresent` branch with an empty `Optional` is covered by the non-idempotent command in `acceptsMissingStoreWhenNoHandlerIsIdempotent`.

- [ ] **Step 4: Run and verify**

Run: `./gradlew :spring-boot-cqrs-core:check`
Expected: PASS with 100% coverage.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-core/src
git commit -m "feat(core): wire the idempotent invoker into the registries after startup"
```

---

### Task 7: Starter auto-configuration (Boot 3 and Boot 4)

**Files (same content in both starters, `<starter>` = `spring-boot-cqrs-boot3-starter` and `spring-boot-cqrs-boot4-starter`):**
- Modify: `<starter>/src/main/java/com/borjaglez/cqrs/autoconfigure/CqrsProperties.java`
- Create: `<starter>/src/main/java/com/borjaglez/cqrs/autoconfigure/CqrsIdempotencyAutoConfiguration.java`
- Modify: `<starter>/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `<starter>/src/test/java/com/borjaglez/cqrs/autoconfigure/CqrsIdempotencyAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `InMemoryIdempotencyStore`, `IdempotentInvoker`, `IdempotencyRegistrar`, `IdempotencyStore`.
- Produces: bean class name `com.borjaglez.cqrs.autoconfigure.CqrsIdempotencyAutoConfiguration` (the JDBC module orders itself before it by name); `CqrsProperties.getIdempotency()` with `getStore()`, `getRetention()`, `getInMemory().getLease()`.

- [ ] **Step 1: Write the failing test (Boot 3 first)**

```java
package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.cqrs.idempotency.IdempotencyRegistrar;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

class CqrsIdempotencyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsIdempotencyAutoConfiguration.class));

  @Test
  void noStoreAndNoInvokerByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(IdempotencyStore.class);
          assertThat(context).doesNotHaveBean(IdempotentInvoker.class);
          assertThat(context).hasSingleBean(IdempotencyRegistrar.class);
        });
  }

  @Test
  void inMemoryStoreOnlyWhenRequested() {
    contextRunner
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              assertThat(context).hasSingleBean(InMemoryIdempotencyStore.class);
              assertThat(context).hasSingleBean(IdempotentInvoker.class);
            });
  }

  @Test
  void userStoreGetsAnInvoker() {
    contextRunner
        .withUserConfiguration(UserStoreConfiguration.class)
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(InMemoryIdempotencyStore.class);
              assertThat(context).hasSingleBean(IdempotentInvoker.class);
            });
  }

  @Test
  void idempotentHandlerWithoutStoreFailsStartup() {
    contextRunner
        .withUserConfiguration(IdempotentHandlerConfiguration.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("are annotated with @Idempotent but no IdempotencyStore"));
  }

  @Test
  void idempotentCommandIsHandledOnce() {
    contextRunner
        .withUserConfiguration(IdempotentHandlerConfiguration.class)
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              CommandBus bus = context.getBean(CommandBus.class);
              Ship command = new Ship();
              bus.dispatch(command);
              bus.dispatch(command);
              assertThat(context.getBean(ShipHandler.class).calls).isEqualTo(1);
            });
  }

  @Test
  void bindsTheIdempotencyProperties() {
    contextRunner
        .withPropertyValues(
            "cqrs.idempotency.store=in-memory",
            "cqrs.idempotency.retention=2d",
            "cqrs.idempotency.in-memory.lease=30s")
        .run(
            context -> {
              CqrsProperties.IdempotencyProperties properties =
                  context.getBean(CqrsProperties.class).getIdempotency();
              assertThat(properties.getStore())
                  .isEqualTo(CqrsProperties.IdempotencyProperties.StoreType.IN_MEMORY);
              assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(2));
              assertThat(properties.getInMemory().getLease()).isEqualTo(Duration.ofSeconds(30));
            });
  }

  @Test
  void idempotencyDefaults() {
    CqrsProperties.IdempotencyProperties properties = new CqrsProperties().getIdempotency();

    assertThat(properties.getStore()).isNull();
    assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(7));
    assertThat(properties.getInMemory().getLease()).isEqualTo(Duration.ofMinutes(5));
  }

  @Configuration(proxyBeanMethods = false)
  static class UserStoreConfiguration {
    @Bean
    IdempotencyStore userStore() {
      return new IdempotencyStore() {
        @Override
        public Acquisition tryAcquire(String handlerId, String messageId) {
          return Acquisition.ACQUIRED;
        }

        @Override
        public void complete(String handlerId, String messageId) {}

        @Override
        public void release(String handlerId, String messageId) {}
      };
    }
  }

  public static class Ship extends Command {}

  @CommandHandler
  public static class ShipHandler {
    int calls;

    @HandleCommand
    @Idempotent
    public void handle(Ship command) {
      calls++;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class IdempotentHandlerConfiguration {
    @Bean
    ShipHandler shipHandler() {
      return new ShipHandler();
    }
  }
}
```

If the existing starter tests show that handler fixtures must live in a separate class or need `@CqrsMessage`, follow that convention.

- [ ] **Step 2: Run and verify it fails**

Run: `./gradlew :spring-boot-cqrs-boot3-starter:test --tests "CqrsIdempotencyAutoConfigurationTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement (Boot 3)**

`CqrsProperties`: add field `private IdempotencyProperties idempotency = new IdempotencyProperties();` after `retry`, and the nested classes:

```java
  @Getter
  @Setter
  public static class IdempotencyProperties {
    /**
     * Store of @Idempotent handlers: jdbc (the default when spring-boot-cqrs-jdbc and a DataSource
     * are present) or in-memory (single instance, tests).
     */
    private StoreType store;

    /** How long a processed message is remembered. */
    private Duration retention = Duration.ofDays(7);

    private InMemoryIdempotencyProperties inMemory = new InMemoryIdempotencyProperties();

    public enum StoreType {
      JDBC,
      IN_MEMORY
    }
  }

  @Getter
  @Setter
  public static class InMemoryIdempotencyProperties {
    /** How long a delivery being processed blocks duplicates in the in-memory store. */
    private Duration lease = Duration.ofMinutes(5);
  }
```

`CqrsIdempotencyAutoConfiguration.java`:

```java
package com.borjaglez.cqrs.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.idempotency.IdempotencyRegistrar;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

/**
 * Deduplicates {@code @Idempotent} handlers. The in-memory store is registered only with {@code
 * cqrs.idempotency.store=in-memory}; {@code spring-boot-cqrs-jdbc} contributes the JDBC store and
 * runs before this configuration.
 */
@AutoConfiguration(after = CqrsAutoConfiguration.class)
@EnableConfigurationProperties(CqrsProperties.class)
public class CqrsIdempotencyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnProperty(prefix = "cqrs.idempotency", name = "store", havingValue = "in-memory")
  public InMemoryIdempotencyStore inMemoryIdempotencyStore(CqrsProperties properties) {
    CqrsProperties.IdempotencyProperties idempotency = properties.getIdempotency();
    return new InMemoryIdempotencyStore(
        idempotency.getRetention(), idempotency.getInMemory().getLease());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(IdempotencyStore.class)
  public IdempotentInvoker idempotentInvoker(IdempotencyStore store) {
    return new IdempotentInvoker(store);
  }

  /** Resolves the invoker only after startup, so the store's {@code DataSource} is not created early. */
  @Bean
  @ConditionalOnMissingBean
  public IdempotencyRegistrar idempotencyRegistrar(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      ObjectProvider<IdempotentInvoker> invoker) {
    return new IdempotencyRegistrar(
        commandHandlerRegistry, eventHandlerRegistry, invoker::getIfAvailable);
  }
}
```

`AutoConfiguration.imports`: append `com.borjaglez.cqrs.autoconfigure.CqrsIdempotencyAutoConfiguration`.

- [ ] **Step 4: Run Boot 3 tests and verify they pass**

Run: `./gradlew :spring-boot-cqrs-boot3-starter:check`
Expected: PASS with coverage.

- [ ] **Step 5: Copy to Boot 4 and run**

Copy `CqrsProperties.java`, `CqrsIdempotencyAutoConfiguration.java`, the `.imports` line and `CqrsIdempotencyAutoConfigurationTest.java` to `spring-boot-cqrs-boot4-starter` at the same paths. Then:

Run: `diff spring-boot-cqrs-boot3-starter/src/main/java/com/borjaglez/cqrs/autoconfigure/CqrsProperties.java spring-boot-cqrs-boot4-starter/src/main/java/com/borjaglez/cqrs/autoconfigure/CqrsProperties.java && diff spring-boot-cqrs-boot3-starter/src/main/java/com/borjaglez/cqrs/autoconfigure/CqrsIdempotencyAutoConfiguration.java spring-boot-cqrs-boot4-starter/src/main/java/com/borjaglez/cqrs/autoconfigure/CqrsIdempotencyAutoConfiguration.java && ./gradlew :spring-boot-cqrs-boot4-starter:check`
Expected: no diff output, PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-boot3-starter/src spring-boot-cqrs-boot4-starter/src
git commit -m "feat(starters): auto-configure idempotent handlers"
```

---

### Task 8: `@CqrsTest` uses the in-memory store

**Files:**
- Modify: `spring-boot-cqrs-test/src/main/java/com/borjaglez/cqrs/test/annotation/CqrsTestConfiguration.java`
- Test: `spring-boot-cqrs-test/src/test/java/com/borjaglez/cqrs/test/annotation/CqrsTestIdempotencyTest.java`

**Interfaces:**
- Consumes: `InMemoryIdempotencyStore`, `IdempotentInvoker`, `IdempotencyRegistrar`.

- [ ] **Step 1: Write the failing test**

Read an existing `@CqrsTest` test in `spring-boot-cqrs-test/src/test` to copy how it adds handler beans (`@Import` or nested `@TestConfiguration`). Then:

```java
package com.borjaglez.cqrs.test.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;

@CqrsTest
class CqrsTestIdempotencyTest {

  @Autowired EventBus eventBus;
  @Autowired Projector projector;

  @Test
  void idempotentHandlersWorkWithoutConfiguration() {
    Published event = new Published();

    eventBus.publish(event);
    eventBus.publish(event);

    assertThat(projector.calls).isEqualTo(1);
  }

  public static class Published extends Event {}

  @EventHandler
  public static class Projector {
    int calls;

    @HandleEvent
    @Idempotent
    public void on(Published event) {
      calls++;
    }
  }

  @TestConfiguration
  static class Handlers {
    @Bean
    Projector projector() {
      return new Projector();
    }
  }
}
```

- [ ] **Step 2: Run and verify it fails**

Run: `./gradlew :spring-boot-cqrs-test:test --tests "CqrsTestIdempotencyTest"`
Expected: FAIL with `IllegalStateException` "is @Idempotent but no IdempotencyStore is configured yet".

- [ ] **Step 3: Implement** — add to `CqrsTestConfiguration`:

```java
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public InMemoryIdempotencyStore inMemoryIdempotencyStore() {
    return new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5));
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public IdempotentInvoker idempotentInvoker(InMemoryIdempotencyStore store) {
    return new IdempotentInvoker(store);
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public IdempotencyRegistrar idempotencyRegistrar(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      IdempotentInvoker idempotentInvoker) {
    return new IdempotencyRegistrar(
        commandHandlerRegistry, eventHandlerRegistry, idempotentInvoker);
  }
```

- [ ] **Step 4: Run and verify**

Run: `./gradlew :spring-boot-cqrs-test:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-test/src
git commit -m "feat(test): deduplicate idempotent handlers in @CqrsTest"
```

---

### Task 9: `spring-boot-cqrs-jdbc` module and `JdbcIdempotencyStore`

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle/libs.versions.toml`
- Create: `spring-boot-cqrs-jdbc/build.gradle.kts`
- Create: `spring-boot-cqrs-jdbc/src/main/java/com/borjaglez/cqrs/jdbc/JdbcIdempotencyStore.java`
- Create: `spring-boot-cqrs-jdbc/src/main/resources/com/borjaglez/cqrs/jdbc/schema-idempotency.sql`
- Test: `spring-boot-cqrs-jdbc/src/test/java/com/borjaglez/cqrs/jdbc/JdbcIdempotencyStoreTest.java`

**Interfaces:**
- Consumes: `IdempotencyStore`, `Acquisition`, `IdempotentInvoker`.
- Produces:
  - `JdbcIdempotencyStore(DataSource, PlatformTransactionManager, String tableName)` and `(…, Clock clock)`
  - `public static final String DEFAULT_TABLE_NAME = "cqrs_processed_message"`
  - `public int deleteProcessedBefore(Instant cutoff)`
  - `public static String validTableName(String tableName)` (throws `IllegalArgumentException`)
  - Resource `com/borjaglez/cqrs/jdbc/schema-idempotency.sql`

- [ ] **Step 1: Build wiring**

`gradle/libs.versions.toml`, `[libraries]`:

```toml
spring-jdbc = { module = "org.springframework:spring-jdbc", version.ref = "spring-framework" }
spring-boot-starter-jdbc = { module = "org.springframework.boot:spring-boot-starter-jdbc" }
postgresql = { module = "org.postgresql:postgresql" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql" }
```

Check how `testcontainers-rabbitmq` resolves with the 2.0.4 BOM (`./gradlew :spring-boot-cqrs-rabbitmq:dependencies --configuration testRuntimeClasspath | grep testcontainers`) and use the same naming scheme for PostgreSQL (`org.testcontainers:testcontainers-postgresql` if the BOM uses the 2.x names).

`settings.gradle.kts`: add `":spring-boot-cqrs-jdbc",` after `":spring-boot-cqrs-rabbitmq",`.

Root `build.gradle.kts`: add `":spring-boot-cqrs-jdbc:jacocoTestCoverageVerification"` to `coverage`, `":spring-boot-cqrs-jdbc:test"` to `quality`, `":spring-boot-cqrs-jdbc:spotlessCheck"` to `spotlessCheckAll`, `":spring-boot-cqrs-jdbc:spotlessApply"` to `spotlessApplyAll`.

`spring-boot-cqrs-jdbc/build.gradle.kts`:

```kotlin
plugins {
    id("cqrs-boot3-library-conventions")
    id("cqrs-publish-conventions")
    id("cqrs-test-conventions")
}

description = "JDBC support for spring-boot-cqrs: transactional idempotency store"

dependencies {
    api(project(":spring-boot-cqrs-core"))
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.jdbc)

    annotationProcessor(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.autoconfigure.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(project(":spring-boot-cqrs-boot3-starter"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.awaitility)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.postgresql)
}
```

Check `cqrs-publish-conventions` for required POM metadata per module (name/description) and add what the other modules set.

- [ ] **Step 2: Schema script**

`schema-idempotency.sql`:

```sql
CREATE TABLE IF NOT EXISTS cqrs_processed_message (
  handler_id   VARCHAR(255) NOT NULL,
  message_id   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMP    NOT NULL,
  PRIMARY KEY (handler_id, message_id)
);
CREATE INDEX IF NOT EXISTS cqrs_processed_message_at ON cqrs_processed_message (processed_at);
```

- [ ] **Step 3: Write the failing test (H2)**

```java
package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;

class JdbcIdempotencyStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  private DataSource dataSource;
  private DataSourceTransactionManager transactionManager;
  private JdbcTemplate jdbc;
  private JdbcIdempotencyStore store;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new ResourceDatabasePopulator(
            new ClassPathResource("com/borjaglez/cqrs/jdbc/schema-idempotency.sql"))
        .execute(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    store =
        new JdbcIdempotencyStore(
            dataSource,
            transactionManager,
            JdbcIdempotencyStore.DEFAULT_TABLE_NAME,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private int markers() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM cqrs_processed_message", Integer.class);
  }

  @Test
  void successfulEffectCommitsItsMarker() {
    IdempotentInvoker invoker = new IdempotentInvoker(store);

    assertThat(invoker.invoke("h", "m", () -> "done").duplicate()).isFalse();
    assertThat(invoker.invoke("h", "m", () -> "again").duplicate()).isTrue();
    assertThat(markers()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT processed_at FROM cqrs_processed_message", java.sql.Timestamp.class))
        .isEqualTo(java.sql.Timestamp.from(NOW));
  }

  @Test
  void failedEffectRollsBackItsMarkerAndItsWork() {
    jdbc.execute("CREATE TABLE effect (id INT)");
    IdempotentInvoker invoker = new IdempotentInvoker(store);

    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m",
                    () -> {
                      jdbc.update("INSERT INTO effect VALUES (1)");
                      throw new IllegalStateException("boom");
                    }))
        .hasMessage("boom");

    assertThat(markers()).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isZero();
    assertThat(invoker.invoke("h", "m", () -> "retried").result()).isEqualTo("retried");
  }

  @Test
  void duplicateInsideAnOuterTransactionKeepsItUsable() {
    jdbc.execute("CREATE TABLE effect (id INT)");
    IdempotentInvoker invoker = new IdempotentInvoker(store);
    invoker.invoke("h", "m", () -> null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(invoker.invoke("h", "m", () -> "x").duplicate()).isTrue();
              jdbc.update("INSERT INTO effect VALUES (1)");
            });

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isOne();
  }

  @Test
  void completeAndReleaseLeaveTheWorkToTheTransaction() {
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
    store.complete("h", "m");
    store.release("h", "m");

    assertThat(markers()).isEqualTo(1);
  }

  @Test
  void deletesMarkersProcessedBeforeACutoff() {
    store.tryAcquire("h", "old");
    JdbcIdempotencyStore later =
        new JdbcIdempotencyStore(
            dataSource,
            transactionManager,
            JdbcIdempotencyStore.DEFAULT_TABLE_NAME,
            Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
    later.tryAcquire("h", "new");

    assertThat(store.deleteProcessedBefore(NOW.plusSeconds(1))).isOne();
    assertThat(jdbc.queryForList("SELECT message_id FROM cqrs_processed_message", String.class))
        .containsExactly("new");
  }

  @Test
  void usesACustomTableName() {
    jdbc.execute(
        "CREATE TABLE markers (handler_id VARCHAR(255) NOT NULL, message_id VARCHAR(64) NOT NULL,"
            + " processed_at TIMESTAMP NOT NULL, PRIMARY KEY (handler_id, message_id))");
    JdbcIdempotencyStore custom = new JdbcIdempotencyStore(dataSource, transactionManager, "markers");

    assertThat(custom.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM markers", Integer.class)).isOne();
  }

  @Test
  void rejectsUnsafeTableNames() {
    assertThatThrownBy(() -> JdbcIdempotencyStore.validTableName("t; DROP TABLE x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table name: t; DROP TABLE x");
    assertThatThrownBy(() -> JdbcIdempotencyStore.validTableName(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table name: null");
    assertThat(JdbcIdempotencyStore.validTableName("cqrs.processed_message"))
        .isEqualTo("cqrs.processed_message");
  }
}
```

- [ ] **Step 4: Run and verify it fails**

Run: `./gradlew :spring-boot-cqrs-jdbc:test --tests "JdbcIdempotencyStoreTest"`
Expected: compilation failure, `JdbcIdempotencyStore` not found.

- [ ] **Step 5: Implement**

```java
package com.borjaglez.cqrs.jdbc;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;

/**
 * {@link IdempotencyStore} backed by a table. {@link #runInScope} runs the handler in a
 * transaction (joining the caller's one when there is one) and {@link #tryAcquire} inserts the
 * marker in it, so the marker commits with the handler's database work and a rollback removes
 * both. A concurrent delivery of the same message blocks on the uncommitted row and then sees a
 * duplicate key. The insert runs in a savepoint so a duplicate does not abort the caller's
 * transaction.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

  public static final String DEFAULT_TABLE_NAME = "cqrs_processed_message";

  private static final Pattern TABLE_NAME =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate scope;
  private final TransactionTemplate savepoint;
  private final String insertSql;
  private final String deleteSql;
  private final Clock clock;

  public JdbcIdempotencyStore(
      DataSource dataSource, PlatformTransactionManager transactionManager, String tableName) {
    this(dataSource, transactionManager, tableName, Clock.systemUTC());
  }

  public JdbcIdempotencyStore(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      String tableName,
      Clock clock) {
    String table = validTableName(tableName);
    this.jdbc = new JdbcTemplate(dataSource);
    this.scope = new TransactionTemplate(transactionManager);
    this.savepoint = new TransactionTemplate(transactionManager);
    this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    this.insertSql =
        "INSERT INTO " + table + " (handler_id, message_id, processed_at) VALUES (?, ?, ?)";
    this.deleteSql = "DELETE FROM " + table + " WHERE processed_at < ?";
    this.clock = clock;
  }

  /** Returns {@code tableName} when it is a plain or schema-qualified SQL identifier. */
  public static String validTableName(String tableName) {
    if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
      throw new IllegalArgumentException("Invalid table name: " + tableName);
    }
    return tableName;
  }

  @Override
  public <T> T runInScope(Supplier<T> work) {
    return scope.execute(status -> work.get());
  }

  @Override
  public Acquisition tryAcquire(String handlerId, String messageId) {
    try {
      savepoint.executeWithoutResult(
          status ->
              jdbc.update(insertSql, handlerId, messageId, Timestamp.from(clock.instant())));
      return Acquisition.ACQUIRED;
    } catch (DuplicateKeyException e) {
      return Acquisition.DUPLICATE;
    }
  }

  /** Nothing to do: the marker is committed with the transaction of {@link #runInScope}. */
  @Override
  public void complete(String handlerId, String messageId) {}

  /** Nothing to do: the rollback of {@link #runInScope}'s transaction removes the marker. */
  @Override
  public void release(String handlerId, String messageId) {}

  /** Deletes the markers of messages processed before {@code cutoff}; returns how many. */
  public int deleteProcessedBefore(Instant cutoff) {
    return jdbc.update(deleteSql, Timestamp.from(cutoff));
  }
}
```

- [ ] **Step 6: Run and verify**

Run: `./gradlew :spring-boot-cqrs-jdbc:test --tests "JdbcIdempotencyStoreTest"`
Expected: PASS. If `duplicateInsideAnOuterTransactionKeepsItUsable` fails on H2 with a nested-transaction error, check that `DataSourceTransactionManager.isNestedTransactionAllowed()` is `true` (it is by default).

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApplyAll
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml spring-boot-cqrs-jdbc
git commit -m "feat(jdbc): add a transactional JDBC idempotency store"
```

---

### Task 10: PostgreSQL and JPA behaviour of the JDBC store

**Files:**
- Test: `spring-boot-cqrs-jdbc/src/test/java/com/borjaglez/cqrs/jdbc/integration/JdbcIdempotencyStorePostgresIntegrationTest.java`
- Test: `spring-boot-cqrs-jdbc/src/test/java/com/borjaglez/cqrs/jdbc/JdbcIdempotencyStoreJpaTest.java`

**Interfaces:**
- Consumes: `JdbcIdempotencyStore`, `IdempotentInvoker`, schema resource.

- [ ] **Step 1: PostgreSQL integration test**

```java
package com.borjaglez.cqrs.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.jdbc.JdbcIdempotencyStore;

@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class JdbcIdempotencyStorePostgresIntegrationTest {

  private static PostgreSQLContainer<?> postgres;
  private static DriverManagerDataSource dataSource;
  private static DataSourceTransactionManager transactionManager;
  private static JdbcTemplate jdbc;

  static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @BeforeAll
  static void start() {
    postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    postgres.start();
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    new ResourceDatabasePopulator(
            new ClassPathResource("com/borjaglez/cqrs/jdbc/schema-idempotency.sql"))
        .execute(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void stop() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM cqrs_processed_message");
  }

  private IdempotentInvoker invoker() {
    return new IdempotentInvoker(
        new JdbcIdempotencyStore(
            dataSource, transactionManager, JdbcIdempotencyStore.DEFAULT_TABLE_NAME));
  }

  @Test
  void concurrentDeliveriesApplyTheEffectOnce() throws Exception {
    IdempotentInvoker invoker = invoker();
    AtomicInteger applied = new AtomicInteger();
    int threads = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return invoker.invoke(
                      "h",
                      "m",
                      () -> {
                        applied.incrementAndGet();
                        sleep(200); // keep the row uncommitted while the others insert
                        return null;
                      });
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(applied).hasValue(1);
  }

  @Test
  void aConcurrentDeliveryRunsWhenTheFirstRollsBack() throws Exception {
    IdempotentInvoker invoker = invoker();
    AtomicInteger applied = new AtomicInteger();
    CountDownLatch firstInserted = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(
              () ->
                  invoker.invoke(
                      "h",
                      "m",
                      () -> {
                        firstInserted.countDown();
                        sleep(300);
                        throw new IllegalStateException("first fails");
                      }));
      firstInserted.await(10, TimeUnit.SECONDS);
      Future<?> second =
          executor.submit(() -> invoker.invoke("h", "m", applied::incrementAndGet));
      assertThat(first)
          .failsWithin(30, TimeUnit.SECONDS)
          .withThrowableThat()
          .withMessageContaining("first fails");
      second.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(applied).hasValue(1);
  }

  @Test
  void duplicateInsideAnOuterTransactionKeepsItUsable() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS effect (id INT)");
    jdbc.update("DELETE FROM effect");
    IdempotentInvoker invoker = invoker();
    invoker.invoke("h", "m", () -> null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(invoker.invoke("h", "m", () -> "x").duplicate()).isTrue();
              jdbc.update("INSERT INTO effect VALUES (1)");
            });

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isOne();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
```

If the Testcontainers 2.x `PostgreSQLContainer` lives in a different package (`org.testcontainers.postgresql`), use the one on the classpath. The `catch (InterruptedException e)` branch is in test code; JaCoCo only measures main code.

- [ ] **Step 2: JPA savepoint test (H2)**

```java
package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.idempotency.IdempotentInvoker;

class JdbcIdempotencyStoreJpaTest {

  @Test
  void duplicateUnderJpaTransactionManagerKeepsTheOuterTransactionUsable() {
    EmbeddedDatabase dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new ResourceDatabasePopulator(
            new ClassPathResource("com/borjaglez/cqrs/jdbc/schema-idempotency.sql"))
        .execute(dataSource);
    LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("com.borjaglez.cqrs.jdbc.none");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.afterPropertiesSet();
    EntityManagerFactory emf = factory.getObject();
    JpaTransactionManager transactionManager = new JpaTransactionManager(emf);
    transactionManager.setDataSource(dataSource);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE effect (id INT)");
    IdempotentInvoker invoker =
        new IdempotentInvoker(
            new JdbcIdempotencyStore(
                dataSource, transactionManager, JdbcIdempotencyStore.DEFAULT_TABLE_NAME));
    invoker.invoke("h", "m", () -> null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(invoker.invoke("h", "m", () -> "x").duplicate()).isTrue();
              jdbc.update("INSERT INTO effect VALUES (1)");
            });

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isOne();
    factory.destroy();
    dataSource.shutdown();
  }
}
```

- [ ] **Step 3: Run**

Run: `./gradlew :spring-boot-cqrs-jdbc:test`
Expected: PASS (the PostgreSQL test is skipped without Docker; run it with Docker before pushing).
If the JPA test fails with `NestedTransactionNotSupportedException`, apply the spec's fallback: keep `PROPAGATION_NESTED` for non-JPA managers, and for others replace the savepoint insert with a dialect-specific insert-if-absent (`INSERT ... ON CONFLICT DO NOTHING` for PostgreSQL/H2, `INSERT IGNORE` for MySQL) where `0` updated rows means `DUPLICATE`. Stop and report to the maintainer before doing that, since it changes the design.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-jdbc/src/test
git commit -m "test(jdbc): cover concurrent duplicates on PostgreSQL and savepoints under JPA"
```

---

### Task 11: JDBC cleanup, schema initializer and auto-configuration

**Files:**
- Create in `spring-boot-cqrs-jdbc/src/main/java/com/borjaglez/cqrs/jdbc/`: `JdbcCqrsProperties.java`, `JdbcIdempotencyCleanup.java`, `JdbcIdempotencySchemaInitializer.java`, `CqrsJdbcIdempotencyAutoConfiguration.java`
- Create: `spring-boot-cqrs-jdbc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `JdbcIdempotencyCleanupTest.java`, `JdbcIdempotencySchemaInitializerTest.java`, `CqrsJdbcIdempotencyAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `JdbcIdempotencyStore` (Task 9), `CqrsIdempotencyAutoConfiguration` class name (Task 7).
- Produces:
  - `JdbcCqrsProperties` (`cqrs.jdbc`): `InitializeSchema initializeSchema = EMBEDDED`; nested `Idempotency { String tableName; boolean cleanupEnabled = true; Duration cleanupInterval = 1h }`.
  - `JdbcIdempotencyCleanup(JdbcIdempotencyStore store, Duration retention, Duration interval, Clock clock) implements SmartLifecycle`, `int purge()`.
  - `JdbcIdempotencySchemaInitializer(DataSource, JdbcCqrsProperties.InitializeSchema mode, String tableName) implements InitializingBean`.

- [ ] **Step 1: Write the failing tests**

`JdbcIdempotencyCleanupTest.java`:

```java
package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class JdbcIdempotencyCleanupTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  private final JdbcIdempotencyStore store = mock(JdbcIdempotencyStore.class);
  private final JdbcIdempotencyCleanup cleanup =
      new JdbcIdempotencyCleanup(
          store, Duration.ofDays(7), Duration.ofMillis(50), Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void purgeDeletesMarkersOlderThanTheRetention() {
    when(store.deleteProcessedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

    assertThat(cleanup.purge()).isEqualTo(3);
  }

  @Test
  void purgeSurvivesDatabaseErrors() {
    when(store.deleteProcessedBefore(any())).thenThrow(new IllegalStateException("db down"));

    assertThat(cleanup.purge()).isZero();
  }

  @Test
  void runsPeriodicallyWhileStarted() {
    assertThat(cleanup.isRunning()).isFalse();

    cleanup.start();
    cleanup.start(); // idempotent
    try {
      assertThat(cleanup.isRunning()).isTrue();
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> verify(store, atLeast(2)).deleteProcessedBefore(any()));
    } finally {
      cleanup.stop();
    }
    cleanup.stop(); // idempotent

    assertThat(cleanup.isRunning()).isFalse();
  }
}
```

`JdbcIdempotencySchemaInitializerTest.java`:

```java
package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;

class JdbcIdempotencySchemaInitializerTest {

  private DataSource embedded() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .setName(UUID.randomUUID().toString())
        .build();
  }

  private boolean tableExists(DataSource dataSource, String table) {
    return new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = ?",
                Integer.class,
                table)
        == 1;
  }

  @Test
  void embeddedModeCreatesTheTableInAnEmbeddedDatabaseTwiceWithoutError() throws Exception {
    DataSource dataSource = embedded();
    new JdbcIdempotencySchemaInitializer(
            dataSource, InitializeSchema.EMBEDDED, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();
    new JdbcIdempotencySchemaInitializer(
            dataSource, InitializeSchema.EMBEDDED, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();

    assertThat(tableExists(dataSource, "cqrs_processed_message")).isTrue();
  }

  @Test
  void embeddedModeSkipsANonEmbeddedDatabase() throws Exception {
    DataSource server = mock(DataSource.class);
    new JdbcIdempotencySchemaInitializer(
            server, InitializeSchema.EMBEDDED, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();
    // EmbeddedDatabaseConnection.isEmbedded asks the mock for a connection, which returns null,
    // and treats it as not embedded; nothing else is called.
  }

  @Test
  void neverModeDoesNothing() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    new JdbcIdempotencySchemaInitializer(
            dataSource, InitializeSchema.NEVER, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();

    verifyNoInteractions(dataSource);
  }

  @Test
  void alwaysModeUsesTheCustomTableName() throws Exception {
    DataSource dataSource = embedded();
    new JdbcIdempotencySchemaInitializer(dataSource, InitializeSchema.ALWAYS, "markers")
        .afterPropertiesSet();

    assertThat(tableExists(dataSource, "markers")).isTrue();
    assertThat(tableExists(dataSource, "cqrs_processed_message")).isFalse();
  }

  @Test
  void rejectsAnUnsafeTableName() {
    assertThatThrownBy(
            () ->
                new JdbcIdempotencySchemaInitializer(
                    mock(DataSource.class), InitializeSchema.ALWAYS, "x; DROP TABLE y"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

Before relying on `embeddedModeSkipsANonEmbeddedDatabase`, read `EmbeddedDatabaseConnection.isEmbedded(DataSource)` in Boot 3.5: if it throws on a `null` connection, replace the mock with a `DriverManagerDataSource` pointing at `jdbc:postgresql://localhost:1/none` and assert no exception (it reads metadata lazily, so check what it does and assert the observable result: the call returns without creating anything).

`CqrsJdbcIdempotencyAutoConfigurationTest.java`:

```java
package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsIdempotencyAutoConfiguration;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

class CqrsJdbcIdempotencyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues("spring.datasource.generate-unique-name=true")
          .withConfiguration(
              AutoConfigurations.of(
                  DataSourceAutoConfiguration.class,
                  DataSourceTransactionManagerAutoConfiguration.class,
                  CqrsAutoConfiguration.class,
                  CqrsIdempotencyAutoConfiguration.class,
                  CqrsJdbcIdempotencyAutoConfiguration.class));

  @Test
  void registersTheJdbcStoreTheInvokerTheSchemaAndTheCleanup() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(JdbcIdempotencyStore.class);
          assertThat(context).hasSingleBean(IdempotentInvoker.class);
          assertThat(context).hasSingleBean(JdbcIdempotencyCleanup.class);
          assertThat(
                  context
                      .getBean(JdbcTemplate.class)
                      .queryForObject("SELECT COUNT(*) FROM cqrs_processed_message", Integer.class))
              .isZero();
        });
  }

  @Test
  void inMemoryStoreSelectedByPropertyWins() {
    contextRunner
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JdbcIdempotencyStore.class);
              assertThat(context).hasSingleBean(InMemoryIdempotencyStore.class);
              assertThat(context).doesNotHaveBean(JdbcIdempotencyCleanup.class);
            });
  }

  @Test
  void cleanupCanBeDisabled() {
    contextRunner
        .withPropertyValues("cqrs.jdbc.idempotency.cleanup-enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(JdbcIdempotencyCleanup.class));
  }

  @Test
  void bindsTheJdbcProperties() {
    contextRunner
        .withPropertyValues(
            "cqrs.jdbc.initialize-schema=always",
            "cqrs.jdbc.idempotency.table-name=markers",
            "cqrs.jdbc.idempotency.cleanup-interval=10m",
            "cqrs.idempotency.retention=1d")
        .run(
            context -> {
              JdbcCqrsProperties properties = context.getBean(JdbcCqrsProperties.class);
              assertThat(properties.getInitializeSchema())
                  .isEqualTo(JdbcCqrsProperties.InitializeSchema.ALWAYS);
              assertThat(properties.getIdempotency().getTableName()).isEqualTo("markers");
              assertThat(properties.getIdempotency().getCleanupInterval())
                  .isEqualTo(Duration.ofMinutes(10));
              assertThat(
                      context
                          .getBean(JdbcTemplate.class)
                          .queryForObject("SELECT COUNT(*) FROM markers", Integer.class))
                  .isZero();
              assertThat(context.getBean(JdbcIdempotencyCleanup.class).retention())
                  .isEqualTo(Duration.ofDays(1));
            });
  }

  @Test
  void userStoreDisablesTheJdbcStore() {
    contextRunner
        .withBean(
            IdempotencyStore.class,
            () -> new InMemoryIdempotencyStore(Duration.ofDays(1), Duration.ofMinutes(1)))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JdbcIdempotencyStore.class);
              assertThat(context).doesNotHaveBean(JdbcIdempotencyCleanup.class);
              assertThat(context).hasSingleBean(IdempotentInvoker.class);
            });
  }

  @Test
  void backsOffWithoutADataSource() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CqrsAutoConfiguration.class,
                CqrsIdempotencyAutoConfiguration.class,
                CqrsJdbcIdempotencyAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(JdbcIdempotencyStore.class));
  }
}
```

Add `JdbcCqrsProperties` defaults test in the same class:

```java
  @Test
  void jdbcPropertyDefaults() {
    JdbcCqrsProperties properties = new JdbcCqrsProperties();

    assertThat(properties.getInitializeSchema())
        .isEqualTo(JdbcCqrsProperties.InitializeSchema.EMBEDDED);
    assertThat(properties.getIdempotency().getTableName())
        .isEqualTo(JdbcIdempotencyStore.DEFAULT_TABLE_NAME);
    assertThat(properties.getIdempotency().isCleanupEnabled()).isTrue();
    assertThat(properties.getIdempotency().getCleanupInterval()).isEqualTo(Duration.ofHours(1));
  }
```

- [ ] **Step 2: Run and verify they fail**

Run: `./gradlew :spring-boot-cqrs-jdbc:test`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`JdbcCqrsProperties.java`:

```java
package com.borjaglez.cqrs.jdbc;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs.jdbc")
public class JdbcCqrsProperties {

  /** When to create the library's tables: embedded (embedded databases only), always, never. */
  private InitializeSchema initializeSchema = InitializeSchema.EMBEDDED;

  private Idempotency idempotency = new Idempotency();

  public enum InitializeSchema {
    EMBEDDED,
    ALWAYS,
    NEVER
  }

  @Getter
  @Setter
  public static class Idempotency {
    /** Table of processed messages; may be schema-qualified. */
    private String tableName = JdbcIdempotencyStore.DEFAULT_TABLE_NAME;

    /** Whether to delete processed-message markers older than cqrs.idempotency.retention. */
    private boolean cleanupEnabled = true;

    /** Delay between two cleanups. */
    private Duration cleanupInterval = Duration.ofHours(1);
  }
}
```

`JdbcIdempotencyCleanup.java`:

```java
package com.borjaglez.cqrs.jdbc;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Deletes processed-message markers older than the retention every {@code interval}, on its own
 * daemon thread. Several instances may run it at once: the delete is idempotent.
 */
public class JdbcIdempotencyCleanup implements SmartLifecycle {

  private static final Log LOG = LogFactory.getLog(JdbcIdempotencyCleanup.class);

  private final JdbcIdempotencyStore store;
  private final Duration retention;
  private final Duration interval;
  private final Clock clock;
  private ScheduledExecutorService executor;

  public JdbcIdempotencyCleanup(
      JdbcIdempotencyStore store, Duration retention, Duration interval, Clock clock) {
    this.store = store;
    this.retention = retention;
    this.interval = interval;
    this.clock = clock;
  }

  public Duration retention() {
    return retention;
  }

  public int purge() {
    try {
      return store.deleteProcessedBefore(clock.instant().minus(retention));
    } catch (RuntimeException e) {
      LOG.warn("Could not delete expired idempotency markers; retrying in " + interval, e);
      return 0;
    }
  }

  @Override
  public synchronized void start() {
    if (executor != null) {
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "cqrs-idempotency-cleanup");
              thread.setDaemon(true);
              return thread;
            });
    long millis = interval.toMillis();
    executor.scheduleWithFixedDelay(this::purge, millis, millis, TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void stop() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return executor != null;
  }
}
```

`JdbcIdempotencySchemaInitializer.java`:

```java
package com.borjaglez.cqrs.jdbc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;

/** Creates the processed-message table from {@value #SCHEMA_LOCATION} when the mode asks for it. */
public class JdbcIdempotencySchemaInitializer implements InitializingBean {

  public static final String SCHEMA_LOCATION = "com/borjaglez/cqrs/jdbc/schema-idempotency.sql";

  private final DataSource dataSource;
  private final InitializeSchema mode;
  private final String tableName;

  public JdbcIdempotencySchemaInitializer(
      DataSource dataSource, InitializeSchema mode, String tableName) {
    this.dataSource = dataSource;
    this.mode = mode;
    this.tableName = JdbcIdempotencyStore.validTableName(tableName);
  }

  @Override
  public void afterPropertiesSet() {
    if (mode == InitializeSchema.NEVER
        || (mode == InitializeSchema.EMBEDDED
            && !EmbeddedDatabaseConnection.isEmbedded(dataSource))) {
      return;
    }
    new ResourceDatabasePopulator(new ByteArrayResource(script().getBytes(StandardCharsets.UTF_8)))
        .execute(dataSource);
  }

  private String script() {
    try {
      return new ClassPathResource(SCHEMA_LOCATION)
          .getContentAsString(StandardCharsets.UTF_8)
          .replace(JdbcIdempotencyStore.DEFAULT_TABLE_NAME, tableName);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
```

The `IOException` branch is unreachable (the script ships in the jar); mark the method with `@lombok.Generated` and a Javadoc that says why, like `MethodHandleUtil` does, or keep the resource read in a static initializer. Prefer the `MethodHandleUtil` pattern for consistency. Note: replacing the table name also renames the index (`cqrs_processed_message_at` → `markers_at`), which is what we want.

`CqrsJdbcIdempotencyAutoConfiguration.java`:

```java
package com.borjaglez.cqrs.jdbc;

import java.time.Clock;
import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.borjaglez.cqrs.idempotency.IdempotencyStore;

/**
 * Contributes the {@link JdbcIdempotencyStore} when the application has a single {@link
 * DataSource} and transaction manager, unless {@code cqrs.idempotency.store=in-memory} or the
 * application defines its own {@link IdempotencyStore}. Runs before the starters' {@code
 * CqrsIdempotencyAutoConfiguration} so it sees the store.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
      "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
      "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
    },
    beforeName = "com.borjaglez.cqrs.autoconfigure.CqrsIdempotencyAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnSingleCandidate(DataSource.class)
@ConditionalOnBean(PlatformTransactionManager.class)
@ConditionalOnProperty(
    prefix = "cqrs.idempotency",
    name = "store",
    havingValue = "jdbc",
    matchIfMissing = true)
@EnableConfigurationProperties(JdbcCqrsProperties.class)
public class CqrsJdbcIdempotencyAutoConfiguration {

  static final Duration DEFAULT_RETENTION = Duration.ofDays(7);

  @Bean
  @ConditionalOnMissingBean
  public JdbcIdempotencySchemaInitializer jdbcIdempotencySchemaInitializer(
      DataSource dataSource, JdbcCqrsProperties properties) {
    return new JdbcIdempotencySchemaInitializer(
        dataSource, properties.getInitializeSchema(), properties.getIdempotency().getTableName());
  }

  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @DependsOn("jdbcIdempotencySchemaInitializer")
  public JdbcIdempotencyStore jdbcIdempotencyStore(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      JdbcCqrsProperties properties) {
    return new JdbcIdempotencyStore(
        dataSource, transactionManager, properties.getIdempotency().getTableName());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcIdempotencyStore.class)
  @ConditionalOnBooleanProperty(name = "cqrs.jdbc.idempotency.cleanup-enabled", matchIfMissing = true)
  public JdbcIdempotencyCleanup jdbcIdempotencyCleanup(
      JdbcIdempotencyStore store, JdbcCqrsProperties properties, Environment environment) {
    Duration retention =
        Binder.get(environment)
            .bind("cqrs.idempotency.retention", Duration.class)
            .orElse(DEFAULT_RETENTION);
    return new JdbcIdempotencyCleanup(
        store, retention, properties.getIdempotency().getCleanupInterval(), Clock.systemUTC());
  }
}
```

If the user overrides the schema initializer bean under another name, `@DependsOn` would fail; keep `@ConditionalOnMissingBean` on the initializer keyed by type and accept that. Read `docs/kafka-adapter.md` or the Kafka auto-configuration to confirm `@ConditionalOnBooleanProperty` exists in the Boot version the module compiles against (the Kafka module already uses it).

Add a test for the default retention when `cqrs.idempotency.retention` is unset in `registersTheJdbcStoreTheInvokerTheSchemaAndTheCleanup`:

```java
          assertThat(context.getBean(JdbcIdempotencyCleanup.class).retention())
              .isEqualTo(Duration.ofDays(7));
```

`AutoConfiguration.imports`:

```
com.borjaglez.cqrs.jdbc.CqrsJdbcIdempotencyAutoConfiguration
```

- [ ] **Step 4: Run and verify**

Run: `./gradlew :spring-boot-cqrs-jdbc:check`
Expected: PASS with 100% coverage.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-jdbc
git commit -m "feat(jdbc): auto-configure the JDBC idempotency store, schema and cleanup"
```

---

### Task 12: RabbitMQ redelivery integration test

**Files:**
- Test: `spring-boot-cqrs-rabbitmq/src/test/java/com/borjaglez/cqrs/rabbitmq/integration/RabbitMqIdempotencyIntegrationTest.java`
- Create: `spring-boot-cqrs-rabbitmq/src/test/java/com/borjaglez/cqrs/rabbitmq/fixtures/IdempotentProjectors.java`

**Interfaces:**
- Consumes: `EventHandlerRegistry.register` (8 args), `setIdempotentInvoker`, `InMemoryIdempotencyStore`, `RabbitMqEventConsumer(registry, middlewares, rabbitTemplate, naming, exchange, appName, contextHeaderPrefix, maxAttempts)`, `RabbitMqBusDeclarationBuilder.buildWithRetryAndDeadLetter(app, exchange, routingKeys, ttl)`.

- [ ] **Step 1: Fixture**

```java
package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

public class IdempotentProjectors {

  public final AtomicInteger stockCalls = new AtomicInteger();
  public final AtomicInteger emailCalls = new AtomicInteger();
  public final AtomicInteger emailFailuresLeft = new AtomicInteger(1);

  public void stock(TestEvent event) {
    stockCalls.incrementAndGet();
  }

  public void email(TestEvent event) {
    if (emailFailuresLeft.getAndDecrement() > 0) {
      throw new IllegalStateException("mail server down");
    }
    emailCalls.incrementAndGet();
  }
}
```

- [ ] **Step 2: Integration test**

```java
package com.borjaglez.cqrs.rabbitmq.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;

import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqEventConsumer;
import com.borjaglez.cqrs.rabbitmq.fixtures.IdempotentProjectors;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.infrastructure.DefaultRabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

/**
 * An event with two idempotent handlers in one application: the second fails once, RabbitMQ
 * redelivers the whole event through the retry queue, and only the failed handler runs again.
 */
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class RabbitMqIdempotencyIntegrationTest {

  private static final String EXCHANGE = "events";
  private static final String APP = "app";
  private static final String ROUTING_KEY = "test.order.created";
  private static final long RETRY_TTL = 200;
  private static final long RECEIVE_TIMEOUT = 5000;

  private static RabbitMQContainer container;
  private static CachingConnectionFactory connectionFactory;
  private static RabbitTemplate rabbitTemplate;
  private static RabbitMqNamingStrategy naming;

  static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @BeforeAll
  static void startBroker() {
    container = new RabbitMQContainer("rabbitmq:3.13-management");
    container.start();
    connectionFactory = new CachingConnectionFactory(container.getHost(), container.getAmqpPort());
    connectionFactory.setUsername(container.getAdminUsername());
    connectionFactory.setPassword(container.getAdminPassword());
    rabbitTemplate = new RabbitTemplate(connectionFactory);
    naming = new DefaultRabbitMqNamingStrategy("it-idempotency");
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    Declarables declarables =
        new RabbitMqBusDeclarationBuilder(naming)
            .buildWithRetryAndDeadLetter(APP, EXCHANGE, List.of(ROUTING_KEY), RETRY_TTL);
    declarables.getDeclarablesByType(TopicExchange.class).forEach(admin::declareExchange);
    declarables.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
    declarables.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
  }

  @AfterAll
  static void stopBroker() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
    if (container != null) {
      container.stop();
    }
  }

  @Test
  void redeliveredEventDoesNotReapplyTheHandlerThatSucceeded() throws Exception {
    IdempotentProjectors projectors = new IdempotentProjectors();
    EventHandlerRegistry registry = new EventHandlerRegistry();
    registry.register(
        TestEvent.class,
        projectors,
        IdempotentProjectors.class.getMethod("stock", TestEvent.class),
        ROUTING_KEY,
        null,
        null,
        true,
        "projectors#stock");
    registry.register(
        TestEvent.class,
        projectors,
        IdempotentProjectors.class.getMethod("email", TestEvent.class),
        ROUTING_KEY,
        null,
        null,
        true,
        "projectors#email");
    registry.setIdempotentInvoker(
        new IdempotentInvoker(
            new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5))));
    RabbitMqEventConsumer consumer =
        new RabbitMqEventConsumer(registry, List.of(), rabbitTemplate, naming, EXCHANGE, APP, null, 3);
    TestEvent event = new TestEvent("payload");

    MessageProperties properties = new MessageProperties();
    properties.setHeader("cqrs.message.type", "event");
    rabbitTemplate.send(
        naming.exchange(EXCHANGE),
        ROUTING_KEY,
        MessageBuilder.withBody("{\"data\":\"payload\"}".getBytes(StandardCharsets.UTF_8))
            .andProperties(properties)
            .build());

    // First delivery: stock succeeds, email fails, the consumer re-sends to the retry queue.
    Message first = rabbitTemplate.receive(naming.queue(APP, EXCHANGE), RECEIVE_TIMEOUT);
    assertThat(first).isNotNull();
    consumer.consume(first, event);

    // Redelivery after the retry TTL, same eventId (the body is unchanged on the wire).
    Message second = rabbitTemplate.receive(naming.queue(APP, EXCHANGE), RECEIVE_TIMEOUT);
    assertThat(second).isNotNull();
    assertThat((Integer) second.getMessageProperties().getHeader("cqrs.redelivery.count"))
        .isEqualTo(1);
    consumer.consume(second, event);

    assertThat(projectors.stockCalls).hasValue(1);
    assertThat(projectors.emailCalls).hasValue(1);
    assertThat(rabbitTemplate.receive(naming.queue(APP, EXCHANGE), RETRY_TTL * 3)).isNull();
    assertThat(rabbitTemplate.receive(naming.queueDeadLetter(APP, EXCHANGE))).isNull();
  }
}
```

Passing the same `TestEvent` instance to both `consume` calls mirrors what deserialization produces for the same body (the existing retry IT does the same). Check `RabbitMqRetryIntegrationTest` for the correct `cqrs.message.type` value used for events.

- [ ] **Step 3: Verify the test fails without deduplication, then passes**

Temporarily remove the two `setIdempotentInvoker`/idempotency-key arguments (register with the 7-argument overload) and run:
Run: `./gradlew :spring-boot-cqrs-rabbitmq:test --tests "RabbitMqIdempotencyIntegrationTest"`
Expected: FAIL, `stockCalls` is 2. Restore the idempotent registration and run again.
Expected: PASS (requires Docker).

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApplyAll
git add spring-boot-cqrs-rabbitmq/src/test
git commit -m "test(rabbitmq): check that a retried event does not reapply idempotent handlers"
```

---

### Task 13: Documentation

**Files:**
- Create: `docs/idempotency.md`
- Modify: `docs/configuration.md` (TOC and a new `## Idempotency Properties` section after `## Retry Properties`, plus the full YAML example), `docs/middleware.md` (a short paragraph at the end of `### RetryMiddleware`), `README.md` (`## Modules` table row, a `### Idempotent handlers` subsection after `### Middleware`, and a `## Documentation` link)

- [ ] **Step 1: Write `docs/idempotency.md`**

~~~~markdown
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
  `DuplicateMessageException`; the result is not stored.
- **Local and remote.** It applies wherever the handler runs. Local dispatch creates a new id per
  message, so it costs one marker write and never skips.
- **Consuming side only.** Deduplication runs in the registries, which only run on the consuming
  side; sending-side middleware (`DispatchPhase.OUTBOUND`) is unaffected.
- **Queries** are not deduplicated (`@Idempotent` on a `@HandleQuery` method fails at startup).

## Stores and guarantees

| Store | Guarantee |
|---|---|
| JDBC (`spring-boot-cqrs-jdbc`) | The marker is inserted in the transaction that runs the handler (the library opens one with `REQUIRED`, so the handler's `@Transactional` joins it). The marker commits with the handler's database work, or both roll back. Two concurrent deliveries: the second waits for the first's row, then skips; if the first rolls back, the second runs. |
| In-memory (`cqrs.idempotency.store=in-memory`) | Per process. A delivery in progress blocks duplicates for `cqrs.idempotency.in-memory.lease`. A crash between the handler's effect and the marker lets a redelivery apply the effect again; markers are lost on restart and not shared between instances. |
| Your own | Implement `IdempotencyStore` and declare it as a bean. Override `runInScope` to run the handler in your store's transaction. |

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

## Retention

Markers older than `cqrs.idempotency.retention` (7 days) are deleted every
`cqrs.jdbc.idempotency.cleanup-interval` (1 hour) by every instance (the delete is idempotent).
Disable it with `cqrs.jdbc.idempotency.cleanup-enabled=false` and purge with
`JdbcIdempotencyStore.deleteProcessedBefore(Instant)` on your own schedule. Keep the retention
longer than the time a message can wait before being redelivered, including replays from a
dead-letter queue.

## With retry

`RetryMiddleware` retries commands in-process. A failed attempt releases the marker, so the next
attempt runs the handler; the order between the two does not matter.

## Without the annotation

`IdempotentInvoker` is a bean; use it in code that is not a library handler, such as a plain
`@KafkaListener`:

```java
invoker.invoke("audit-listener", record.key(), () -> {
  audit.save(record.value());
  return null;
});
```
~~~~

- [ ] **Step 2: `docs/configuration.md`**

Add `- [Idempotency Properties](#idempotency-properties)` to the TOC after Retry, and after the Retry section:

```markdown
## Idempotency Properties

See [Idempotent Handlers](idempotency.md).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cqrs.idempotency.store` | `jdbc` / `in-memory` | unset | Store of `@Idempotent` handlers. Unset uses the JDBC store when `spring-boot-cqrs-jdbc` and a `DataSource` are present. |
| `cqrs.idempotency.retention` | `Duration` | `7d` | How long a processed message is remembered |
| `cqrs.idempotency.in-memory.lease` | `Duration` | `5m` | How long a delivery in progress blocks duplicates in the in-memory store |
| `cqrs.jdbc.initialize-schema` | `embedded` / `always` / `never` | `embedded` | When to create the processed-message table |
| `cqrs.jdbc.idempotency.table-name` | `String` | `cqrs_processed_message` | Table of processed messages; may be schema-qualified |
| `cqrs.jdbc.idempotency.cleanup-enabled` | `boolean` | `true` | Delete markers older than the retention periodically |
| `cqrs.jdbc.idempotency.cleanup-interval` | `Duration` | `1h` | Delay between two cleanups |
```

Add the same keys with their defaults to the `## Full YAML Example` block under `cqrs:`.

- [ ] **Step 3: `docs/middleware.md`** — at the end of `### RetryMiddleware`:

```markdown
Retrying an `@Idempotent` command is safe: a failed attempt leaves no marker, so the next attempt
runs the handler. Events are still not retried in-process; with `@Idempotent` handlers a transport
redelivery only re-runs the handlers that failed. See [Idempotent Handlers](idempotency.md).
```

- [ ] **Step 4: `README.md`**

- `## Modules`: add a row `| spring-boot-cqrs-jdbc | JDBC idempotency store for @Idempotent handlers |` matching the table's columns.
- After `### Middleware` (before `### Message Context & Correlation ID`):

```markdown
### Idempotent handlers

RabbitMQ and Kafka deliver at least once. Annotate a command or event handler with `@Idempotent`
to run it at most once per message; add `spring-boot-cqrs-jdbc` so the marker commits in the same
transaction as the handler's work. See [docs/idempotency.md](docs/idempotency.md).
```

- `## Documentation`: add `- [Idempotent Handlers](docs/idempotency.md) -- @Idempotent, stores, schema, retention`.

- [ ] **Step 5: Commit**

```bash
git add docs/idempotency.md docs/configuration.md docs/middleware.md README.md
git commit -m "docs: document idempotent handlers"
```

---

### Task 14: Full verification and pull request

- [ ] **Step 1: Format and run the gates**

Run: `./gradlew spotlessApplyAll && ./gradlew quality verifyBoot3Compatibility verifyBoot4Compatibility`
Expected: BUILD SUCCESSFUL. With Docker running, confirm in the test reports that `JdbcIdempotencyStorePostgresIntegrationTest` and `RabbitMqIdempotencyIntegrationTest` ran (not skipped): `find . -path "*test-results*" -name "*Idempotency*Integration*.xml" | xargs grep -l 'skipped="0"'`.

- [ ] **Step 2: Check the constraints**

Run: `git diff origin/main --stat -- CHANGELOG.md gradle.properties .github` → no output.
Run: `git log origin/main..HEAD --format=%B | grep -iE "claude|co-authored|generated with|\bAI\b"` → no output.

- [ ] **Step 3: Push and open the pull request** (only after the maintainer confirms; pushing a feature branch triggers `feature-snapshot.yml`)

```bash
git push -u origin feat/2-idempotent-handlers
gh pr create --title "feat: deduplicate redelivered commands and events with @Idempotent" \
  --label enhancement --label area:core --label area:starters \
  --body-file <body from .github/pull_request_template.md with "Closes #2" and a short explanation>
```

Do not merge; the maintainer squash-merges once CI is green.
