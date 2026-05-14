package com.borjaglez.cqrs.test.assertion;

import com.borjaglez.cqrs.test.bus.SpyCommandBus;
import com.borjaglez.cqrs.test.bus.SpyEventBus;
import com.borjaglez.cqrs.test.bus.SpyQueryBus;

public final class CqrsAssertions {

  private CqrsAssertions() {}

  public static CommandBusAssert assertThat(SpyCommandBus bus) {
    return new CommandBusAssert(bus);
  }

  public static EventBusAssert assertThat(SpyEventBus bus) {
    return new EventBusAssert(bus);
  }

  public static QueryBusAssert assertThat(SpyQueryBus bus) {
    return new QueryBusAssert(bus);
  }
}
