package com.borjaglez.cqrs.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.ResultType;
import com.borjaglez.cqrs.fixtures.TestCommand;

class CommandBusTest {

  @Test
  void dispatchAndReceiveWithResultTypeDelegatesToDispatchAndReceive() {
    CommandBus bus =
        new CommandBus() {
          @Override
          public void dispatch(Command command) {}

          @Override
          public void dispatchAndWait(Command command) {}

          @Override
          @SuppressWarnings("unchecked")
          public <R> R dispatchAndReceive(Command command) {
            return (R) ("handled:" + ((TestCommand) command).getData());
          }
        };

    String result = bus.dispatchAndReceive(new TestCommand("data"), new ResultType<String>() {});

    assertThat(result).isEqualTo("handled:data");
  }
}
