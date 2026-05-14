package com.borjaglez.cqrs.test.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.test.fixtures.TestEvent;

class InMemoryEventBusTest {

  @Test
  void publishInvokesAllSubscribers() {
    InMemoryEventBus bus = new InMemoryEventBus();
    List<String> received = new ArrayList<>();
    bus.subscribe(TestEvent.class, e -> received.add("a-" + e.getPayload()));
    bus.subscribe(TestEvent.class, e -> received.add("b-" + e.getPayload()));

    bus.publish(new TestEvent("x"));

    assertThat(received).containsExactly("a-x", "b-x");
  }

  @Test
  void publishWithoutSubscribersIsSilent() {
    InMemoryEventBus bus = new InMemoryEventBus();

    bus.publish(new TestEvent("x"));
  }

  @Test
  void publishListIteratesEvents() {
    InMemoryEventBus bus = new InMemoryEventBus();
    List<String> received = new ArrayList<>();
    bus.subscribe(TestEvent.class, e -> received.add(e.getPayload()));

    bus.publish(List.<Event>of(new TestEvent("a"), new TestEvent("b")));

    assertThat(received).containsExactly("a", "b");
  }

  @Test
  void subscriberExceptionPropagates() {
    InMemoryEventBus bus = new InMemoryEventBus();
    bus.subscribe(
        TestEvent.class,
        e -> {
          throw new IllegalStateException("boom");
        });

    assertThatThrownBy(() -> bus.publish(new TestEvent("x")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void subscribeReturnsBusForChaining() {
    InMemoryEventBus bus = new InMemoryEventBus();
    InMemoryEventBus returned = bus.subscribe(TestEvent.class, e -> {});

    assertThat(returned).isSameAs(bus);
  }
}
