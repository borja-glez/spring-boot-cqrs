package com.borjaglez.cqrs.event.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.fixtures.CheckedThrowingEventHandler;
import com.borjaglez.cqrs.fixtures.IdempotentEventHandlers;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestEventHandler;
import com.borjaglez.cqrs.fixtures.ThrowingEventHandler;
import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class EventHandlerRegistryTest {

  private EventHandlerRegistry registry;
  private Logger registryLogger;
  private ListAppender<ILoggingEvent> logs;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    registry = new EventHandlerRegistry();
    registryLogger = (Logger) LoggerFactory.getLogger(EventHandlerRegistry.class);
    previousLevel = registryLogger.getLevel();
    registryLogger.setLevel(Level.DEBUG);
    logs = new ListAppender<>();
    logs.start();
    registryLogger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    registryLogger.detachAppender(logs);
    registryLogger.setLevel(previousLevel);
    logs.stop();
  }

  @Test
  void handlersAreRemoteUnlessRegisteredAsLocal() throws Exception {
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, new TestEventHandler(), method, "test.event");
    registry.register(TestEvent.class, new TestEventHandler(), method, "test.event", false);

    assertThat(registry.getHandlerInfos(TestEvent.class))
        .extracting(EventHandlerRegistry.HandlerInfo::remote)
        .containsExactly(true, false);
  }

  @Test
  void handlerInfoWithoutTheRemoteFlagIsRemote() {
    assertThat(new EventHandlerRegistry.HandlerInfo(new Object(), null, "test.event").remote())
        .isTrue();
  }

  @Test
  void handleRunsLocalAndRemoteHandlers() throws Exception {
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    TestEventHandler remote = new TestEventHandler();
    TestEventHandler local = new TestEventHandler();
    registry.register(TestEvent.class, remote, method, "test.event");
    registry.register(TestEvent.class, local, method, "test.event", false);

    registry.handle(new TestEvent("both"));

    assertThat(remote.getLastHandledData()).isEqualTo("both");
    assertThat(local.getLastHandledData()).isEqualTo("both");
  }

  @Test
  void handleRemoteRunsOnlyRemoteHandlers() throws Exception {
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    TestEventHandler remote = new TestEventHandler();
    TestEventHandler local = new TestEventHandler();
    registry.register(TestEvent.class, remote, method, "test.event");
    registry.register(TestEvent.class, local, method, "test.event", false);

    registry.handleRemote(new TestEvent("remote"));

    assertThat(remote.getLastHandledData()).isEqualTo("remote");
    assertThat(local.getLastHandledData()).isNull();
  }

  @Test
  void handleRemoteOfAnUnhandledEventDoesNothing() {
    registry.handleRemote(new TestEvent("nobody"));

    assertThat(logs.list).isEmpty();
  }

  @Test
  void eventSubclassWithOnlySuperclassHandlerLogsOneWarningAndIsNotHandled() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler, method, "test.event");

    registry.handle(new SubTestEvent("first"));
    registry.handle(new SubTestEvent("second"));

    assertThat(handler.getLastHandledData()).isNull();
    assertThat(logs.list).hasSize(1);
    ILoggingEvent warning = logs.list.get(0);
    assertThat(warning.getLevel()).isEqualTo(Level.WARN);
    assertThat(warning.getFormattedMessage())
        .contains(SubTestEvent.class.getName())
        .contains(TestEvent.class.getName())
        .contains("exact message class");
  }

  @Test
  void eventWithoutAnyHandlerInHierarchyLogsNothing() {
    registry.handle(new TestEvent("nobody-listening"));

    assertThat(logs.list).isEmpty();
  }

  @Test
  void registerAndHandle() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler, method, "test.event");

    TestEvent event = new TestEvent("hello");
    registry.handle(event);

    assertThat(handler.getLastHandledData()).isEqualTo("hello");
  }

  @Test
  void multipleHandlersForSameEvent() throws Exception {
    TestEventHandler handler1 = new TestEventHandler();
    TestEventHandler handler2 = new TestEventHandler();
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler1, method, "test.event");
    registry.register(TestEvent.class, handler2, method, "test.event");

    TestEvent event = new TestEvent("multi");
    registry.handle(event);

    assertThat(handler1.getLastHandledData()).isEqualTo("multi");
    assertThat(handler2.getLastHandledData()).isEqualTo("multi");
  }

  @Test
  void handleWithNoRegisteredHandlersDoesNotThrow() {
    TestEvent event = new TestEvent("nobody-listening");
    registry.handle(event);
    // no exception
  }

  @Test
  void handleRethrowsRuntimeException() throws Exception {
    ThrowingEventHandler handler = new ThrowingEventHandler();
    Method method = ThrowingEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler, method, "test.event");

    assertThatThrownBy(() -> registry.handle(new TestEvent("data")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("event handler error");
  }

  @Test
  void getRegisteredEventsReturnsSet() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler, method, "test.event");

    assertThat(registry.getRegisteredEvents()).containsExactly(TestEvent.class);
  }

  @Test
  void getHandlerInfosReturnsList() throws Exception {
    TestEventHandler handler1 = new TestEventHandler();
    TestEventHandler handler2 = new TestEventHandler();
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler1, method, "test.event.1");
    registry.register(TestEvent.class, handler2, method, "test.event.2");

    var infos = registry.getHandlerInfos(TestEvent.class);

    assertThat(infos).hasSize(2);
    assertThat(infos.get(0).messageName()).isEqualTo("test.event.1");
    assertThat(infos.get(1).messageName()).isEqualTo("test.event.2");
  }

  @Test
  void getHandlerInfosReturnsEmptyForUnregistered() {
    assertThat(registry.getHandlerInfos(TestEvent.class)).isEmpty();
  }

  @Test
  void handleWrapsCheckedExceptionInEventHandlerExecutionException() throws Exception {
    CheckedThrowingEventHandler handler = new CheckedThrowingEventHandler();
    Method method = CheckedThrowingEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler, method, "test.event");

    assertThatThrownBy(() -> registry.handle(new TestEvent("data")))
        .isInstanceOf(EventHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  private static final SpelExpressionParser PARSER = new SpelExpressionParser();

  private static Method handleMethod() throws NoSuchMethodException {
    return TestEventHandler.class.getMethod("handle", TestEvent.class);
  }

  private static Expression condition(String expression) {
    return PARSER.parseExpression(expression);
  }

  @Test
  void conditionOnEventPropertyTrueInvokesHandler() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    registry.register(
        TestEvent.class, handler, handleMethod(), "test.event", condition("data == 'go'"), null);

    registry.handle(new TestEvent("go"));

    assertThat(handler.getLastHandledData()).isEqualTo("go");
  }

  @Test
  void conditionFalseSkipsOnlyThatHandler() throws Exception {
    TestEventHandler filtered = new TestEventHandler();
    TestEventHandler unconditional = new TestEventHandler();
    registry.register(
        TestEvent.class,
        filtered,
        handleMethod(),
        "test.event",
        condition("#event.data == 'go'"),
        null);
    registry.register(TestEvent.class, unconditional, handleMethod(), "test.event");

    registry.handle(new TestEvent("stop"));

    assertThat(filtered.getLastHandledData()).isNull();
    assertThat(unconditional.getLastHandledData()).isEqualTo("stop");
  }

  @Test
  void nullConditionBehavesLikeNoCondition() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    registry.register(TestEvent.class, handler, handleMethod(), "test.event", null, null);

    registry.handle(new TestEvent("always"));

    assertThat(handler.getLastHandledData()).isEqualTo("always");
    assertThat(registry.getHandlerInfos(TestEvent.class).get(0).condition()).isNull();
  }

  @Test
  void conditionResolvesBeanReferencesThroughBeanResolver() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    FeatureFlags flags = new FeatureFlags("notify");
    BeanResolver beanResolver = (context, beanName) -> flags;
    registry.register(
        TestEvent.class,
        handler,
        handleMethod(),
        "test.event",
        condition("@featureFlags.enabled(data)"),
        beanResolver);

    registry.handle(new TestEvent("other"));
    assertThat(handler.getLastHandledData()).isNull();

    registry.handle(new TestEvent("notify"));
    assertThat(handler.getLastHandledData()).isEqualTo("notify");
  }

  @Test
  void conditionReturningNullRaisesEventHandlerExecutionException() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    registry.register(
        TestEvent.class, handler, handleMethod(), "test.event", condition("data"), null);

    assertThatThrownBy(() -> registry.handle(new TestEvent(null)))
        .isInstanceOf(EventHandlerExecutionException.class)
        .hasMessageContaining("'data'")
        .hasMessageContaining("TestEventHandler.handle(")
        .hasMessageContaining("null");
    assertThat(handler.getLastHandledData()).isNull();
  }

  @Test
  void conditionReturningNonBooleanRaisesEventHandlerExecutionException() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    registry.register(
        TestEvent.class, handler, handleMethod(), "test.event", condition("data"), null);

    assertThatThrownBy(() -> registry.handle(new TestEvent("text")))
        .isInstanceOf(EventHandlerExecutionException.class)
        .hasMessageContaining("'data'")
        .hasMessageContaining("TestEventHandler.handle(")
        .hasMessageContaining(String.class.getName());
    assertThat(handler.getLastHandledData()).isNull();
  }

  @Test
  void conditionThrowingDuringEvaluationRaisesEventHandlerExecutionException() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    registry.register(
        TestEvent.class, handler, handleMethod(), "test.event", condition("missing == 1"), null);

    assertThatThrownBy(() -> registry.handle(new TestEvent("x")))
        .isInstanceOf(EventHandlerExecutionException.class)
        .hasMessageContaining("'missing == 1'")
        .hasMessageContaining("TestEventHandler.handle(")
        .hasCauseInstanceOf(org.springframework.expression.EvaluationException.class);
    assertThat(handler.getLastHandledData()).isNull();
  }

  @Test
  void handlerInfoExposesCondition() throws Exception {
    Expression expression = condition("data == 'go'");
    registry.register(
        TestEvent.class, new TestEventHandler(), handleMethod(), "test.event", expression, null);

    var info = registry.getHandlerInfos(TestEvent.class).get(0);

    assertThat(info.condition()).isNotNull();
    assertThat(info.condition().expression()).isSameAs(expression);
    assertThat(info.condition().handler()).contains("TestEventHandler.handle(");
  }

  @Test
  void handlerInfoThreeArgumentConstructorHasNoCondition() {
    var info = new EventHandlerRegistry.HandlerInfo("bean", null, "test.event");

    assertThat(info.bean()).isEqualTo("bean");
    assertThat(info.messageName()).isEqualTo("test.event");
    assertThat(info.condition()).isNull();
  }

  public static class FeatureFlags {
    private final String enabledFeature;

    FeatureFlags(String enabledFeature) {
      this.enabledFeature = enabledFeature;
    }

    public boolean enabled(String feature) {
      return enabledFeature.equals(feature);
    }
  }

  static class SubTestEvent extends TestEvent {
    SubTestEvent(String data) {
      super(data);
    }
  }

  @Test
  void findsTheHandledEventByItsMessageName() throws Exception {
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, new TestEventHandler(), method, "test.event");

    assertThat(registry.findMessageClass("test.event")).contains(TestEvent.class);
    assertThat(registry.findMessageClass("other.event")).isEmpty();
  }

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
            EventHandlerRegistry.HandlerInfo::handlerId,
            EventHandlerRegistry.HandlerInfo::idempotent)
        .containsExactly(
            tuple("projector#first", true), tuple("projector#second", true), tuple(null, false));
  }

  @Test
  void handlerInfoWithThePreviousCanonicalSignatureIsNotIdempotent() {
    EventHandlerRegistry.HandlerInfo info =
        new EventHandlerRegistry.HandlerInfo(
            new Object(), MethodHandles.constant(String.class, "x"), "name", null, false);

    assertThat(info.handlerId()).isNull();
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
}
