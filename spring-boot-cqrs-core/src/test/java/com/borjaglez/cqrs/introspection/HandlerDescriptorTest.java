package com.borjaglez.cqrs.introspection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestEventHandler;

class HandlerDescriptorTest {

  @Test
  void accessors() {
    HandlerDescriptor descriptor =
        new HandlerDescriptor(
            TestCommand.class, HandlerType.COMMAND, "test.command", TestCommandHandler.class, true);

    assertThat(descriptor.messageType()).isEqualTo(TestCommand.class);
    assertThat(descriptor.handlerType()).isEqualTo(HandlerType.COMMAND);
    assertThat(descriptor.messageName()).isEqualTo("test.command");
    assertThat(descriptor.handlerBeanType()).isEqualTo(TestCommandHandler.class);
    assertThat(descriptor.requiresValidation()).isTrue();
  }

  @Test
  void equalityAndHashCode() {
    HandlerDescriptor d1 =
        new HandlerDescriptor(
            TestCommand.class,
            HandlerType.COMMAND,
            "test.command",
            TestCommandHandler.class,
            false);
    HandlerDescriptor d2 =
        new HandlerDescriptor(
            TestCommand.class,
            HandlerType.COMMAND,
            "test.command",
            TestCommandHandler.class,
            false);
    HandlerDescriptor d3 =
        new HandlerDescriptor(
            TestEvent.class, HandlerType.EVENT, "test.event", TestEventHandler.class, false);

    assertThat(d1).isEqualTo(d2);
    assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
    assertThat(d1).isNotEqualTo(d3);
  }

  @Test
  void toStringContainsFields() {
    HandlerDescriptor descriptor =
        new HandlerDescriptor(
            TestCommand.class,
            HandlerType.COMMAND,
            "test.command",
            TestCommandHandler.class,
            false);

    String str = descriptor.toString();
    assertThat(str).contains("test.command");
    assertThat(str).contains("COMMAND");
  }
}
