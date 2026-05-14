package com.borjaglez.cqrs.test.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.test.assertion.EventBusAssert;
import com.borjaglez.cqrs.test.fixtures.TestEvent;

class SpyEventBusTest {

  @Test
  void publishRecordsAndDelegates() {
    EventBus delegate = mock(EventBus.class);
    SpyEventBus spy = new SpyEventBus(delegate);
    TestEvent event = new TestEvent("a");

    spy.publish(event);

    verify(delegate).publish(event);
    assertThat(spy.recorded()).containsExactly(event);
  }

  @Test
  void publishListRecordsAndDelegates() {
    EventBus delegate = mock(EventBus.class);
    SpyEventBus spy = new SpyEventBus(delegate);
    List<Event> events = List.of(new TestEvent("a"), new TestEvent("b"));

    spy.publish(events);

    verify(delegate).publish(events);
    assertThat(spy.recorded()).containsExactlyElementsOf(events);
  }

  @Test
  void clearEmptiesRecording() {
    SpyEventBus spy = new SpyEventBus(mock(EventBus.class));
    spy.publish(new TestEvent("a"));

    spy.clear();

    assertThat(spy.recorded()).isEmpty();
  }

  @Test
  void assertProviderReturnsAssert() {
    SpyEventBus spy = new SpyEventBus(mock(EventBus.class));

    EventBusAssert provided = spy.assertThat();

    assertThat(provided).isNotNull();
  }
}
