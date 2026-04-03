package com.borjaglez.cqrs.event.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.middleware.BusMiddleware;

@ExtendWith(MockitoExtension.class)
class SpringEventBusTest {

  @Test
  void publishSingleEvent() {
    EventHandlerRegistry registry = mock(EventHandlerRegistry.class);
    SpringEventBus bus = new SpringEventBus(registry, List.of());

    TestEvent event = new TestEvent("data");
    bus.publish(event);

    verify(registry).handle(event);
  }

  @Test
  void publishListOfEvents() {
    EventHandlerRegistry registry = mock(EventHandlerRegistry.class);
    SpringEventBus bus = new SpringEventBus(registry, List.of());

    TestEvent event1 = new TestEvent("one");
    TestEvent event2 = new TestEvent("two");
    bus.publish(List.of(event1, event2));

    verify(registry).handle(event1);
    verify(registry).handle(event2);
  }

  @Test
  void middlewareChainIsInvoked() {
    EventHandlerRegistry registry = mock(EventHandlerRegistry.class);
    List<String> callOrder = new ArrayList<>();

    BusMiddleware middleware =
        (msg, chain) -> {
          callOrder.add("middleware");
          return chain.proceed(msg);
        };

    SpringEventBus bus = new SpringEventBus(registry, List.of(middleware));
    bus.publish(new TestEvent("data"));

    assertThat(callOrder).containsExactly("middleware");
    verify(registry).handle(any());
  }

  @Test
  void runtimeExceptionPropagated() {
    EventHandlerRegistry registry = mock(EventHandlerRegistry.class);
    doThrow(new IllegalStateException("runtime")).when(registry).handle(any());

    SpringEventBus bus = new SpringEventBus(registry, List.of());

    assertThatThrownBy(() -> bus.publish(new TestEvent("data")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkedExceptionWrapped() {
    EventHandlerRegistry registry = mock(EventHandlerRegistry.class);

    BusMiddleware throwingMiddleware =
        (msg, chain) -> {
          throw new Exception("checked");
        };

    SpringEventBus bus = new SpringEventBus(registry, List.of(throwingMiddleware));

    assertThatThrownBy(() -> bus.publish(new TestEvent("data")))
        .isInstanceOf(EventHandlerExecutionException.class)
        .hasCauseInstanceOf(Exception.class);
  }
}
