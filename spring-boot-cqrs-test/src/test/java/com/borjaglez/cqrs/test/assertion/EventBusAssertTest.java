package com.borjaglez.cqrs.test.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.test.bus.SpyEventBus;
import com.borjaglez.cqrs.test.fixtures.AnotherTestEvent;
import com.borjaglez.cqrs.test.fixtures.TestEvent;

class EventBusAssertTest {

  private final SpyEventBus spy = new SpyEventBus(mock(EventBus.class));

  @Test
  void publishedFiltersByType() {
    spy.publish(new TestEvent("a"));
    spy.publish(new AnotherTestEvent());

    new EventBusAssert(spy).published(TestEvent.class).once();
  }

  @Test
  void onceFailsWhenCountDiffers() {
    spy.publish(new TestEvent("a"));
    spy.publish(new TestEvent("b"));

    assertThatThrownBy(() -> new EventBusAssert(spy).published(TestEvent.class).once())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void neverPassesWhenNoMatches() {
    spy.publish(new AnotherTestEvent());

    new EventBusAssert(spy).published(TestEvent.class).never();
  }

  @Test
  void timesFailsWhenCountDiffers() {
    spy.publish(new TestEvent("a"));

    assertThatThrownBy(() -> new EventBusAssert(spy).published(TestEvent.class).times(3))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Expected 3")
        .hasMessageContaining("got 1");
  }

  @Test
  void matchingPassesWhenPredicateMatches() {
    spy.publish(new TestEvent("hit"));

    new EventBusAssert(spy)
        .published(TestEvent.class)
        .matching(e -> ((TestEvent) e).getPayload().equals("hit"));
  }

  @Test
  void matchingFailsWhenNoneMatches() {
    spy.publish(new TestEvent("miss"));

    assertThatThrownBy(
            () ->
                new EventBusAssert(spy)
                    .published(TestEvent.class)
                    .matching(e -> ((TestEvent) e).getPayload().equals("hit")))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("predicate");
  }

  @Test
  void unfilteredAssertionUsesDefaultDescription() {
    spy.publish(new TestEvent("a"));

    assertThatThrownBy(() -> new EventBusAssert(spy).times(2))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("any event");
  }

  @Test
  void unfilteredMatchingUsesDefaultDescription() {
    spy.publish(new TestEvent("a"));

    assertThatThrownBy(() -> new EventBusAssert(spy).matching(e -> false))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("any event");
  }

  @Test
  void assertProviderIsHonoredByAssertJEntryPoint() {
    spy.publish(new TestEvent("a"));

    assertThat(spy).published(TestEvent.class).once();
  }
}
