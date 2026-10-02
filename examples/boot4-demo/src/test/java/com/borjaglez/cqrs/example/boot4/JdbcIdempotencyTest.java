package com.borjaglez.cqrs.example.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.example.boot4.event.OrderCreatedEvent;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.jdbc.JdbcIdempotencyStore;

/**
 * On Spring Boot 4, spring-boot-cqrs-jdbc with an embedded H2 database contributes the JDBC store,
 * creates its table, and an {@code @Idempotent} handler runs once per event.
 */
@SpringBootTest
class JdbcIdempotencyTest {

  @Autowired private IdempotencyStore store;
  @Autowired private IdempotentInvoker invoker;
  @Autowired private EventHandlerRegistry eventHandlerRegistry;
  @Autowired private CountingProjector projector;

  @Test
  void theJdbcStoreIsConfigured() {
    assertThat(store).isInstanceOf(JdbcIdempotencyStore.class);
    assertThat(invoker).isNotNull();
  }

  @Test
  void anIdempotentHandlerRunsOncePerEvent() {
    OrderCreatedEvent event = new OrderCreatedEvent("order-1", "book", 1);

    eventHandlerRegistry.handle(event);
    eventHandlerRegistry.handle(event);

    assertThat(projector.calls).hasValue(1);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Handlers {

    @Bean
    CountingProjector countingProjector() {
      return new CountingProjector();
    }
  }

  @EventHandler
  static class CountingProjector {

    final AtomicInteger calls = new AtomicInteger();

    @HandleEvent
    @Idempotent(name = "counting-projector")
    public void on(OrderCreatedEvent event) {
      calls.incrementAndGet();
    }
  }
}
