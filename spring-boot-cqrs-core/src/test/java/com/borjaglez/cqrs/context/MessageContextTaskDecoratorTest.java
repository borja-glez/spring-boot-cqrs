package com.borjaglez.cqrs.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class MessageContextTaskDecoratorTest {

  @AfterEach
  void cleanUp() {
    MessageContext.clear();
  }

  @Test
  void decoratedExecutorPropagatesTheCallerContextAndClearsTheWorker() throws Exception {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setTaskDecorator(new MessageContextTaskDecorator());
    executor.initialize();
    AtomicReference<String> seen = new AtomicReference<>("unset");
    try {
      try (MessageContext.Scope ignored =
          MessageContext.scope(MessageContext.empty().with("correlationId", "req-1"))) {
        executor.submit(() -> seen.set(MessageContext.current().correlationId())).get();
      }
      assertThat(seen.get()).isEqualTo("req-1");

      executor.submit(() -> seen.set(MessageContext.current().correlationId())).get();
      assertThat(seen.get()).isNull();
    } finally {
      executor.shutdown();
    }
  }
}
