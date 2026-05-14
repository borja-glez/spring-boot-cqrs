package com.borjaglez.cqrs.test.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.test.bus.SpyCommandBus;
import com.borjaglez.cqrs.test.bus.SpyEventBus;
import com.borjaglez.cqrs.test.bus.SpyQueryBus;

class CqrsAssertionsTest {

  @Test
  void assertThatReturnsCommandBusAssert() {
    SpyCommandBus spy = new SpyCommandBus(mock(CommandBus.class));

    CommandBusAssert provided = CqrsAssertions.assertThat(spy);

    assertThat(provided).isNotNull();
  }

  @Test
  void assertThatReturnsEventBusAssert() {
    SpyEventBus spy = new SpyEventBus(mock(EventBus.class));

    EventBusAssert provided = CqrsAssertions.assertThat(spy);

    assertThat(provided).isNotNull();
  }

  @Test
  void assertThatReturnsQueryBusAssert() {
    SpyQueryBus spy = new SpyQueryBus(mock(QueryBus.class));

    QueryBusAssert provided = CqrsAssertions.assertThat(spy);

    assertThat(provided).isNotNull();
  }
}
