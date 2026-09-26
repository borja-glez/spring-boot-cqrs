package com.borjaglez.cqrs.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;

class MessageContextThreadLocalAccessorTest {

  private final MessageContextThreadLocalAccessor accessor =
      new MessageContextThreadLocalAccessor();

  @AfterEach
  void cleanUp() {
    MessageContext.clear();
  }

  @Test
  void keyIsStable() {
    assertThat(accessor.key())
        .isEqualTo(MessageContextThreadLocalAccessor.KEY)
        .isEqualTo("cqrs.messageContext");
  }

  @Test
  void getValueIsNullWhenTheContextIsEmpty() {
    assertThat(accessor.getValue()).isNull();
  }

  @Test
  void getValueReturnsTheCurrentContext() {
    MessageContext ctx = MessageContext.empty().with("correlationId", "c-1");
    try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
      assertThat(accessor.getValue()).isEqualTo(ctx);
    }
  }

  @Test
  void setValueInstallsTheContextAndSetValueWithoutArgumentsClearsIt() {
    MessageContext ctx = MessageContext.empty().with("correlationId", "c-1");
    accessor.setValue(ctx);
    assertThat(MessageContext.current()).isEqualTo(ctx);

    accessor.setValue();
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void setValueWithNullOrEmptyClearsTheContext() {
    accessor.setValue(MessageContext.empty().with("correlationId", "c-1"));
    accessor.setValue(null);
    assertThat(MessageContext.current().isEmpty()).isTrue();

    accessor.setValue(MessageContext.empty().with("correlationId", "c-1"));
    accessor.setValue(MessageContext.empty());
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void restoreReinstallsThePreviousValueOrClears() {
    MessageContext previous = MessageContext.empty().with("correlationId", "prev");
    accessor.setValue(MessageContext.empty().with("correlationId", "other"));
    accessor.restore(previous);
    assertThat(MessageContext.current()).isEqualTo(previous);

    accessor.restore();
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void contextPropagatingTaskDecoratorCarriesTheMessageContext() throws Exception {
    ContextRegistry registry = new ContextRegistry().registerThreadLocalAccessor(accessor);
    ContextSnapshotFactory factory =
        ContextSnapshotFactory.builder().contextRegistry(registry).build();
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator(factory));
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
