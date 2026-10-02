package com.borjaglez.cqrs.test.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;

@CqrsTest
@Import(CqrsTestIdempotencyTest.Handlers.class)
class CqrsTestIdempotencyTest {

  @Autowired EventBus eventBus;
  @Autowired Projector projector;

  @Test
  void idempotentHandlersWorkWithoutConfiguration() {
    Published event = new Published();

    eventBus.publish(event);
    eventBus.publish(event);

    assertThat(projector.calls).isEqualTo(1);
  }

  public static class Published extends Event {}

  @EventHandler
  public static class Projector {
    int calls;

    @HandleEvent
    @Idempotent
    public void on(Published event) {
      calls++;
    }
  }

  @TestConfiguration
  static class Handlers {
    @Bean
    Projector projector() {
      return new Projector();
    }
  }
}
