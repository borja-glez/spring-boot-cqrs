package com.borjaglez.cqrs.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestEvent;

class EventTest {

  @Test
  void eventIdIsGenerated() {
    TestEvent event = new TestEvent("data");
    assertThat(event.getEventId()).isNotNull().isNotEmpty();
  }

  @Test
  void occurredOnIsSet() {
    TestEvent event = new TestEvent("data");
    assertThat(event.getOccurredOn()).isNotNull();
  }

  @Test
  void twoEventsHaveDifferentIds() {
    TestEvent event1 = new TestEvent("data");
    TestEvent event2 = new TestEvent("data");
    assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
  }

  @Test
  void equalsBasedOnEventId() {
    TestEvent event = new TestEvent("data");
    assertThat(event).isEqualTo(event);
  }

  @Test
  void notEqualToDifferentEvent() {
    TestEvent event1 = new TestEvent("data");
    TestEvent event2 = new TestEvent("data");
    assertThat(event1).isNotEqualTo(event2);
  }

  @Test
  void notEqualToNull() {
    TestEvent event = new TestEvent("data");
    assertThat(event).isNotEqualTo(null);
  }

  @Test
  void notEqualToDifferentType() {
    TestEvent event = new TestEvent("data");
    assertThat(event).isNotEqualTo("not an event");
  }

  @Test
  void hashCodeBasedOnEventId() {
    TestEvent event = new TestEvent("data");
    assertThat(event.hashCode()).isEqualTo(event.hashCode());
  }

  @Test
  void dataIsStored() {
    TestEvent event = new TestEvent("hello");
    assertThat(event.getData()).isEqualTo("hello");
  }
}
