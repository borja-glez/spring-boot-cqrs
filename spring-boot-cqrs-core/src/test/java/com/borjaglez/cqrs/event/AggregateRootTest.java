package com.borjaglez.cqrs.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestAggregateRoot;
import com.borjaglez.cqrs.fixtures.TestEvent;

class AggregateRootTest {

  @Test
  void recordAndPullEventsReturnsEvents() {
    TestAggregateRoot aggregate = new TestAggregateRoot();
    aggregate.doSomething();

    List<Event> events = aggregate.pullEvents();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(TestEvent.class);
    assertThat(((TestEvent) events.get(0)).getData()).isEqualTo("happened");
  }

  @Test
  void pullEventsClearsList() {
    TestAggregateRoot aggregate = new TestAggregateRoot();
    aggregate.doSomething();
    aggregate.doSomething();

    List<Event> firstPull = aggregate.pullEvents();
    assertThat(firstPull).hasSize(2);

    List<Event> secondPull = aggregate.pullEvents();
    assertThat(secondPull).isEmpty();
  }

  @Test
  void pullEventsOnEmptyReturnsEmptyList() {
    TestAggregateRoot aggregate = new TestAggregateRoot();
    List<Event> events = aggregate.pullEvents();
    assertThat(events).isEmpty();
  }
}
