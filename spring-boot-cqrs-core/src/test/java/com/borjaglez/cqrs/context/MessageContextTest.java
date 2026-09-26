package com.borjaglez.cqrs.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessageContextTest {

  @AfterEach
  void cleanUp() {
    MessageContext.clear();
  }

  @Test
  void emptyIsImmutableSingleton() {
    assertThat(MessageContext.empty().isEmpty()).isTrue();
    assertThat(MessageContext.empty().asMap()).isEmpty();
    assertThat(MessageContext.empty().correlationId()).isNull();
    assertThat(MessageContext.empty().get("anything")).isEmpty();
  }

  @Test
  void ofNullOrEmptyReturnsEmpty() {
    assertThat(MessageContext.of(null)).isSameAs(MessageContext.empty());
    assertThat(MessageContext.of(new HashMap<>())).isSameAs(MessageContext.empty());

    Map<String, String> nulls = new HashMap<>();
    nulls.put(null, "v");
    nulls.put("k", null);
    assertThat(MessageContext.of(nulls)).isSameAs(MessageContext.empty());
  }

  @Test
  void ofCopiesEntriesAndIsImmutable() {
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("correlationId", "abc");
    entries.put("tenantId", "acme");

    MessageContext ctx = MessageContext.of(entries);
    entries.put("mutation", "bad");

    assertThat(ctx.asMap()).containsOnlyKeys("correlationId", "tenantId");
    assertThat(ctx.correlationId()).isEqualTo("abc");
    assertThat(ctx.get("tenantId")).contains("acme");
    assertThatThrownBy(() -> ctx.asMap().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void withReturnsNewInstance() {
    MessageContext ctx = MessageContext.empty().with("a", "1");
    MessageContext next = ctx.with("b", "2");

    assertThat(ctx.asMap()).containsOnlyKeys("a");
    assertThat(next.asMap()).containsOnlyKeys("a", "b");
    assertThat(ctx).isNotSameAs(next);
  }

  @Test
  void withRejectsNulls() {
    assertThatThrownBy(() -> MessageContext.empty().with(null, "v"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MessageContext.empty().with("k", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void mergeHandlesNullEmptyAndPrecedence() {
    MessageContext a = MessageContext.empty().with("k1", "a").with("shared", "a");
    MessageContext b = MessageContext.empty().with("k2", "b").with("shared", "b");

    assertThat(a.merge(null)).isSameAs(a);
    assertThat(a.merge(MessageContext.empty())).isSameAs(a);
    assertThat(MessageContext.empty().merge(b)).isSameAs(b);

    MessageContext merged = a.merge(b);
    assertThat(merged.asMap())
        .containsEntry("k1", "a")
        .containsEntry("k2", "b")
        .containsEntry("shared", "b");
  }

  @Test
  void scopeSetsAndRestoresPrevious() {
    assertThat(MessageContext.current().isEmpty()).isTrue();

    MessageContext outer = MessageContext.empty().with("x", "1");
    try (MessageContext.Scope s1 = MessageContext.scope(outer)) {
      assertThat(MessageContext.current()).isEqualTo(outer);

      MessageContext inner = outer.with("y", "2");
      try (MessageContext.Scope s2 = MessageContext.scope(inner)) {
        assertThat(MessageContext.current()).isEqualTo(inner);
      }

      assertThat(MessageContext.current()).isEqualTo(outer);
    }

    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void scopeCloseIsIdempotent() {
    MessageContext ctx = MessageContext.empty().with("k", "v");
    MessageContext.Scope scope = MessageContext.scope(ctx);
    scope.close();
    scope.close();
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void scopeAcceptsNull() {
    MessageContext.Scope scope = MessageContext.scope(null);
    assertThat(MessageContext.current().isEmpty()).isTrue();
    scope.close();
  }

  @Test
  void equalsAndHashCodeAndToString() {
    MessageContext a = MessageContext.empty().with("k", "v");
    MessageContext b = MessageContext.empty().with("k", "v");
    MessageContext c = MessageContext.empty().with("k", "w");

    assertThat(a).isEqualTo(a).isEqualTo(b).isNotEqualTo(c).isNotEqualTo("str").isNotEqualTo(null);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).contains("k=v");
  }

  @Test
  void wrappedRunnableSeesTheCallerContextOnAnotherThread() throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.empty().with("correlationId", "req-1"))) {
      Runnable task = MessageContext.wrap(() -> seen.set(MessageContext.current().correlationId()));
      executor.submit(task).get();
    } finally {
      executor.shutdownNow();
    }
    assertThat(seen.get()).isEqualTo("req-1");
  }

  @Test
  void wrappedCallableSeesTheCallerContextOnAnotherThread() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.empty().with("correlationId", "req-1"))) {
      Callable<String> task = MessageContext.wrap(() -> MessageContext.current().correlationId());
      assertThat(executor.submit(task).get()).isEqualTo("req-1");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void wrappedTaskCapturesTheContextAtWrapTime() throws Exception {
    Callable<String> task;
    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.empty().with("correlationId", "at-wrap"))) {
      task = MessageContext.wrap(() -> MessageContext.current().correlationId());
    }
    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.empty().with("correlationId", "at-run"))) {
      assertThat(task.call()).isEqualTo("at-wrap");
      assertThat(MessageContext.current().correlationId()).isEqualTo("at-run");
    }
  }

  @Test
  void workerContextIsClearedAfterAWrappedTask() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      try (MessageContext.Scope ignored =
          MessageContext.scope(MessageContext.empty().with("correlationId", "req-1"))) {
        Runnable task = MessageContext.wrap(() -> {});
        executor.submit(task).get();
      }
      assertThat(executor.submit(MessageContext::current).get().isEmpty()).isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void wrappedTaskReplacesAndRestoresTheRunningThreadContext() throws Exception {
    Callable<Boolean> emptyTask = MessageContext.wrap(() -> MessageContext.current().isEmpty());
    MessageContext workerContext = MessageContext.empty().with("correlationId", "worker");
    try (MessageContext.Scope ignored = MessageContext.scope(workerContext)) {
      assertThat(emptyTask.call()).isTrue();
      assertThat(MessageContext.current()).isEqualTo(workerContext);
    }
  }

  @Test
  void wrappedTaskRestoresTheContextWhenItFails() throws Exception {
    Runnable failing =
        () -> {
          throw new IllegalStateException("boom");
        };
    Callable<Object> failingCallable =
        () -> {
          throw new Exception("checked");
        };
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      try (MessageContext.Scope ignored =
          MessageContext.scope(MessageContext.empty().with("correlationId", "req-1"))) {
        Runnable wrapped = MessageContext.wrap(failing);
        Callable<Object> wrappedCallable = MessageContext.wrap(failingCallable);
        assertThatThrownBy(() -> executor.submit(wrapped).get())
            .isInstanceOf(ExecutionException.class)
            .hasRootCauseMessage("boom");
        assertThatThrownBy(() -> executor.submit(wrappedCallable).get())
            .isInstanceOf(ExecutionException.class)
            .hasRootCauseMessage("checked");
      }
      assertThat(executor.submit(MessageContext::current).get().isEmpty()).isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void wrapRejectsNullTasks() {
    assertThatThrownBy(() -> MessageContext.wrap((Runnable) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("task");
    assertThatThrownBy(() -> MessageContext.wrap((Callable<?>) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("task");
  }
}
