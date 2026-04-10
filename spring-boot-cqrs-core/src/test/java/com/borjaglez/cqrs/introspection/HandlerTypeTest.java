package com.borjaglez.cqrs.introspection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HandlerTypeTest {

  @Test
  void valuesContainsAllTypes() {
    HandlerType[] values = HandlerType.values();
    assertThat(values).containsExactly(HandlerType.COMMAND, HandlerType.EVENT, HandlerType.QUERY);
  }

  @Test
  void valueOfReturnsCorrectType() {
    assertThat(HandlerType.valueOf("COMMAND")).isEqualTo(HandlerType.COMMAND);
    assertThat(HandlerType.valueOf("EVENT")).isEqualTo(HandlerType.EVENT);
    assertThat(HandlerType.valueOf("QUERY")).isEqualTo(HandlerType.QUERY);
  }
}
