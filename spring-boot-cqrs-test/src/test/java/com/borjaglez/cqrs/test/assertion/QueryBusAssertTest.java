package com.borjaglez.cqrs.test.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.test.bus.SpyQueryBus;
import com.borjaglez.cqrs.test.fixtures.AnotherTestQuery;
import com.borjaglez.cqrs.test.fixtures.TestQuery;

class QueryBusAssertTest {

  private final SpyQueryBus spy = new SpyQueryBus(mock(QueryBus.class));

  @Test
  void askedFiltersByType() {
    spy.ask(new TestQuery("a"));
    spy.ask(new AnotherTestQuery());

    new QueryBusAssert(spy).asked(TestQuery.class).once();
  }

  @Test
  void onceFailsWhenCountDiffers() {
    spy.ask(new TestQuery("a"));
    spy.ask(new TestQuery("b"));

    assertThatThrownBy(() -> new QueryBusAssert(spy).asked(TestQuery.class).once())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void neverPassesWhenNoMatches() {
    spy.ask(new AnotherTestQuery());

    new QueryBusAssert(spy).asked(TestQuery.class).never();
  }

  @Test
  void timesFailsWhenCountDiffers() {
    spy.ask(new TestQuery("a"));

    assertThatThrownBy(() -> new QueryBusAssert(spy).asked(TestQuery.class).times(3))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Expected 3")
        .hasMessageContaining("got 1");
  }

  @Test
  void matchingPassesWhenPredicateMatches() {
    spy.ask(new TestQuery("hit"));

    new QueryBusAssert(spy)
        .asked(TestQuery.class)
        .matching(q -> ((TestQuery) q).getPayload().equals("hit"));
  }

  @Test
  void matchingFailsWhenNoneMatches() {
    spy.ask(new TestQuery("miss"));

    assertThatThrownBy(
            () ->
                new QueryBusAssert(spy)
                    .asked(TestQuery.class)
                    .matching(q -> ((TestQuery) q).getPayload().equals("hit")))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("predicate");
  }

  @Test
  void unfilteredAssertionUsesDefaultDescription() {
    spy.ask(new TestQuery("a"));

    assertThatThrownBy(() -> new QueryBusAssert(spy).times(2))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("any query");
  }

  @Test
  void unfilteredMatchingUsesDefaultDescription() {
    spy.ask(new TestQuery("a"));

    assertThatThrownBy(() -> new QueryBusAssert(spy).matching(q -> false))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("any query");
  }

  @Test
  void assertProviderIsHonoredByAssertJEntryPoint() {
    spy.ask(new TestQuery("a"));

    assertThat(spy).asked(TestQuery.class).once();
  }
}
