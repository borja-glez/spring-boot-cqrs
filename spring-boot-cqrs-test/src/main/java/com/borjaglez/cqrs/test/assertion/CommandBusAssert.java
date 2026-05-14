package com.borjaglez.cqrs.test.assertion;

import java.util.List;
import java.util.function.Predicate;

import org.assertj.core.api.AbstractAssert;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.test.bus.SpyCommandBus;

public class CommandBusAssert extends AbstractAssert<CommandBusAssert, SpyCommandBus> {

  private List<Command> filtered;
  private String filterDescription = "any command";

  public CommandBusAssert(SpyCommandBus actual) {
    super(actual, CommandBusAssert.class);
    isNotNull();
    this.filtered = actual.recorded();
  }

  public CommandBusAssert dispatched(Class<? extends Command> type) {
    this.filtered = filtered.stream().filter(type::isInstance).toList();
    this.filterDescription = type.getName();
    return this;
  }

  public CommandBusAssert once() {
    return times(1);
  }

  public CommandBusAssert never() {
    return times(0);
  }

  public CommandBusAssert times(int expected) {
    int actualCount = filtered.size();
    if (actualCount != expected) {
      throw failure(
          "Expected %d dispatch(es) of %s but got %d", expected, filterDescription, actualCount);
    }
    return this;
  }

  public CommandBusAssert matching(Predicate<? super Command> predicate) {
    if (filtered.stream().noneMatch(predicate)) {
      throw failure("No dispatched command of %s matched the given predicate", filterDescription);
    }
    return this;
  }
}
