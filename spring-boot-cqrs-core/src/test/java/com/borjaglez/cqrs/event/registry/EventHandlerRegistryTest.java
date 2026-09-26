package com.borjaglez.cqrs.event.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

  static class SubTestEvent extends TestEvent {
    SubTestEvent(String data) {
      super(data);
    }
  }
}
