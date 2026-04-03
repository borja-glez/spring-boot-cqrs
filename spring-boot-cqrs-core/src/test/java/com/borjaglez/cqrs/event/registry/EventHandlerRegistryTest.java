package com.borjaglez.cqrs.event.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.fixtures.CheckedThrowingEventHandler;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestEventHandler;
import com.borjaglez.cqrs.fixtures.ThrowingEventHandler;

class EventHandlerRegistryTest {

  private EventHandlerRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new EventHandlerRegistry();
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
  void handleWrapsCheckedExceptionInEventHandlerExecutionException() throws Exception {
    CheckedThrowingEventHandler handler = new CheckedThrowingEventHandler();
    Method method = CheckedThrowingEventHandler.class.getMethod("handle", TestEvent.class);
    registry.register(TestEvent.class, handler, method, "test.event");

    assertThatThrownBy(() -> registry.handle(new TestEvent("data")))
        .isInstanceOf(EventHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }
}
