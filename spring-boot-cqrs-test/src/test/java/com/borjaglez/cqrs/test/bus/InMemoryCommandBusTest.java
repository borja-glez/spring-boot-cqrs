package com.borjaglez.cqrs.test.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.test.fixtures.TestCommand;

class InMemoryCommandBusTest {

  @Test
  void dispatchAndReceiveReturnsHandlerResult() {
    InMemoryCommandBus bus = new InMemoryCommandBus();
    bus.register(TestCommand.class, cmd -> "handled-" + cmd.getPayload());

    String result = bus.dispatchAndReceive(new TestCommand("a"));

    assertThat(result).isEqualTo("handled-a");
  }

  @Test
  void dispatchAndReceiveWithTypeReferenceReturnsHandlerResult() {
    InMemoryCommandBus bus = new InMemoryCommandBus();
    bus.register(TestCommand.class, cmd -> "v");

    String result =
        bus.dispatchAndReceive(new TestCommand("a"), new ParameterizedTypeReference<String>() {});

    assertThat(result).isEqualTo("v");
  }

  @Test
  void dispatchInvokesHandlerAndIgnoresResult() {
    InMemoryCommandBus bus = new InMemoryCommandBus();
    StringBuilder sink = new StringBuilder();
    bus.register(
        TestCommand.class,
        cmd -> {
          sink.append(cmd.getPayload());
          return null;
        });

    bus.dispatch(new TestCommand("x"));

    assertThat(sink.toString()).isEqualTo("x");
  }

  @Test
  void dispatchAndWaitInvokesHandler() {
    InMemoryCommandBus bus = new InMemoryCommandBus();
    StringBuilder sink = new StringBuilder();
    bus.register(
        TestCommand.class,
        cmd -> {
          sink.append("waited");
          return null;
        });

    bus.dispatchAndWait(new TestCommand("x"));

    assertThat(sink.toString()).isEqualTo("waited");
  }

  @Test
  void dispatchWithoutHandlerThrows() {
    InMemoryCommandBus bus = new InMemoryCommandBus();

    assertThatThrownBy(() -> bus.dispatch(new TestCommand("a")))
        .isInstanceOf(NoHandlerRegisteredException.class)
        .hasMessageContaining(TestCommand.class.getName());
  }

  @Test
  void registerReturnsBusForChaining() {
    InMemoryCommandBus bus = new InMemoryCommandBus();
    InMemoryCommandBus returned = bus.register(TestCommand.class, cmd -> null);

    assertThat(returned).isSameAs(bus);
  }
}
