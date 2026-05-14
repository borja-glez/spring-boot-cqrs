package com.borjaglez.cqrs.test.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.test.bus.SpyCommandBus;
import com.borjaglez.cqrs.test.fixtures.AnotherTestCommand;
import com.borjaglez.cqrs.test.fixtures.TestCommand;

class CommandBusAssertTest {

  private final SpyCommandBus spy = new SpyCommandBus(mock(CommandBus.class));

  @Test
  void dispatchedFiltersByType() {
    spy.dispatch(new TestCommand("a"));
    spy.dispatch(new AnotherTestCommand());

    new CommandBusAssert(spy).dispatched(TestCommand.class).once();
  }

  @Test
  void onceFailsWhenCountDiffers() {
    spy.dispatch(new TestCommand("a"));
    spy.dispatch(new TestCommand("b"));

    assertThatThrownBy(() -> new CommandBusAssert(spy).dispatched(TestCommand.class).once())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Expected 1")
        .hasMessageContaining("got 2");
  }

  @Test
  void neverPassesWhenNoMatches() {
    spy.dispatch(new AnotherTestCommand());

    new CommandBusAssert(spy).dispatched(TestCommand.class).never();
  }

  @Test
  void neverFailsWhenSomeMatches() {
    spy.dispatch(new TestCommand("a"));

    assertThatThrownBy(() -> new CommandBusAssert(spy).dispatched(TestCommand.class).never())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void timesPassesForExactCount() {
    spy.dispatch(new TestCommand("a"));
    spy.dispatch(new TestCommand("b"));
    spy.dispatch(new TestCommand("c"));

    new CommandBusAssert(spy).dispatched(TestCommand.class).times(3);
  }

  @Test
  void matchingPassesWhenPredicateMatches() {
    spy.dispatch(new TestCommand("hit"));
    spy.dispatch(new TestCommand("miss"));

    new CommandBusAssert(spy)
        .dispatched(TestCommand.class)
        .matching(c -> ((TestCommand) c).getPayload().equals("hit"));
  }

  @Test
  void matchingFailsWhenNoneMatches() {
    spy.dispatch(new TestCommand("miss"));

    assertThatThrownBy(
            () ->
                new CommandBusAssert(spy)
                    .dispatched(TestCommand.class)
                    .matching(c -> ((TestCommand) c).getPayload().equals("hit")))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("predicate");
  }

  @Test
  void unfilteredAssertionUsesDefaultDescription() {
    spy.dispatch(new TestCommand("a"));

    assertThatThrownBy(() -> new CommandBusAssert(spy).times(2))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("any command");
  }

  @Test
  void unfilteredMatchingUsesDefaultDescription() {
    spy.dispatch(new TestCommand("a"));

    assertThatThrownBy(() -> new CommandBusAssert(spy).matching(c -> false))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("any command");
  }

  @Test
  void assertProviderIsHonoredByAssertJEntryPoint() {
    spy.dispatch(new TestCommand("a"));

    assertThat(spy).dispatched(TestCommand.class).once();
  }
}
