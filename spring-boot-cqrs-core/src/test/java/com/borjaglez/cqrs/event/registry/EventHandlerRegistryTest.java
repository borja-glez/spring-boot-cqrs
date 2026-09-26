package com.borjaglez.cqrs.event.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.fixtures.CheckedThrowingEventHandler;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestEventHandler;
import com.borjaglez.cqrs.fixtures.ThrowingEventHandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class EventHandlerRegistryTest {

  private EventHandlerRegistry registry;
  private Logger registryLogger;
  private ListAppender<ILoggingEvent> logs;

  @BeforeEach
  void setUp() {
    registry = new EventHandlerRegistry();
    registryLogger = (Logger) LoggerFactory.getLogger(EventHandlerRegistry.class);
    logs = new ListAppender<>();
    logs.start();
    registryLogger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    registryLogger.detachAppender(logs);
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
}
