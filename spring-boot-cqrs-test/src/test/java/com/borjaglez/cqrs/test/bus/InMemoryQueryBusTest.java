package com.borjaglez.cqrs.test.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.test.fixtures.TestQuery;

class InMemoryQueryBusTest {

  @Test
  void askReturnsHandlerResult() {
    InMemoryQueryBus bus = new InMemoryQueryBus();
    bus.register(TestQuery.class, q -> "answered-" + q.getPayload());

    String result = bus.ask(new TestQuery("a"));

    assertThat(result).isEqualTo("answered-a");
  }

  @Test
  void askWithTypeReferenceReturnsHandlerResult() {
    InMemoryQueryBus bus = new InMemoryQueryBus();
    bus.register(TestQuery.class, q -> "v");

    String result = bus.ask(new TestQuery("a"), new ParameterizedTypeReference<String>() {});

    assertThat(result).isEqualTo("v");
  }

  @Test
  void askWithoutHandlerThrows() {
    InMemoryQueryBus bus = new InMemoryQueryBus();

    assertThatThrownBy(() -> bus.ask(new TestQuery("a")))
        .isInstanceOf(NoHandlerRegisteredException.class)
        .hasMessageContaining(TestQuery.class.getName());
  }

  @Test
  void registerReturnsBusForChaining() {
    InMemoryQueryBus bus = new InMemoryQueryBus();
    InMemoryQueryBus returned = bus.register(TestQuery.class, q -> null);

    assertThat(returned).isSameAs(bus);
  }
}
