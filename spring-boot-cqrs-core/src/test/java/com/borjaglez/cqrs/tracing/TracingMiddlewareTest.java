package com.borjaglez.cqrs.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

class TracingMiddlewareTest {

  private TestObservationRegistry registry;
  private TracingMiddleware middleware;

  @BeforeEach
  void setUp() {
    registry = TestObservationRegistry.create();
    middleware = new TracingMiddleware(registry, null);
  }

  @Test
  void createsObservationForCommand() throws Exception {
    MiddlewareChain chain = msg -> "ok";

    middleware.process(new TestCommand("data"), chain);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo(TracingMiddleware.DEFAULT_OBSERVATION_NAME)
        .that()
        .hasLowCardinalityKeyValue("cqrs.message.kind", "command")
        .hasLowCardinalityKeyValue("cqrs.message.type", "TestCommand");
  }

  @Test
  void createsObservationForEvent() throws Exception {
    MiddlewareChain chain = msg -> null;

    middleware.process(new TestEvent("data"), chain);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo(TracingMiddleware.DEFAULT_OBSERVATION_NAME)
        .that()
        .hasLowCardinalityKeyValue("cqrs.message.kind", "event");
  }

  @Test
  void createsObservationForQuery() throws Exception {
    MiddlewareChain chain = msg -> "answer";

    middleware.process(new TestQuery("data"), chain);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo(TracingMiddleware.DEFAULT_OBSERVATION_NAME)
        .that()
        .hasLowCardinalityKeyValue("cqrs.message.kind", "query");
  }

  @Test
  void tagsUnknownKindForNonCqrsMessage() throws Exception {
    MiddlewareChain chain = msg -> null;

    middleware.process("plain-string", chain);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo(TracingMiddleware.DEFAULT_OBSERVATION_NAME)
        .that()
        .hasLowCardinalityKeyValue("cqrs.message.kind", "unknown")
        .hasLowCardinalityKeyValue("cqrs.message.type", "String");
  }

  @Test
  void returnsHandlerResult() throws Exception {
    MiddlewareChain chain = msg -> "result";

    Object result = middleware.process(new TestCommand("data"), chain);

    assertThat(result).isEqualTo("result");
  }

  @Test
  void recordsExceptionAndPropagates() {
    RuntimeException boom = new IllegalStateException("boom");
    MiddlewareChain chain =
        msg -> {
          throw boom;
        };

    assertThatThrownBy(() -> middleware.process(new TestCommand("data"), chain)).isSameAs(boom);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo(TracingMiddleware.DEFAULT_OBSERVATION_NAME)
        .that()
        .hasError(boom);
  }

  @Test
  void nullObservationNameFallsBackToDefault() throws Exception {
    TracingMiddleware mw = new TracingMiddleware(registry, null);

    mw.process(new TestCommand("data"), msg -> null);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo(TracingMiddleware.DEFAULT_OBSERVATION_NAME);
  }

  @Test
  void customObservationNameIsUsed() throws Exception {
    TracingMiddleware mw = new TracingMiddleware(registry, "my.app.dispatch");

    mw.process(new TestCommand("data"), msg -> null);

    TestObservationRegistryAssert.assertThat(registry)
        .hasObservationWithNameEqualTo("my.app.dispatch");
  }
}
