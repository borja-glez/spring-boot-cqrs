package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class IdempotentInvokerTest {

  private IdempotencyStore store;
  private IdempotentInvoker invoker;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    store = mock(IdempotencyStore.class);
    when(store.runInScope(any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    invoker = new IdempotentInvoker(store);
  }

  @Test
  void runsTheEffectAndCompletesWhenAcquired() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.ACQUIRED);

    Outcome<String> outcome = invoker.invoke("h", "m", () -> "done");

    assertThat(outcome).isEqualTo(Outcome.applied("done"));
    assertThat(outcome.duplicate()).isFalse();
    InOrder order = inOrder(store);
    order.verify(store).tryAcquire("h", "m");
    order.verify(store).complete("h", "m");
    verify(store, never()).release("h", "m");
  }

  @Test
  void skipsTheEffectWhenDuplicate() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.DUPLICATE);
    AtomicInteger runs = new AtomicInteger();

    Outcome<Integer> outcome = invoker.invoke("h", "m", runs::incrementAndGet);

    assertThat(outcome).isEqualTo(Outcome.skipped());
    assertThat(outcome.duplicate()).isTrue();
    assertThat(outcome.result()).isNull();
    assertThat(runs).hasValue(0);
    verify(store, never()).complete("h", "m");
  }

  @Test
  void releasesAndRethrowsWhenTheEffectFails() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.ACQUIRED);
    IllegalStateException failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    verify(store).release("h", "m");
    verify(store, never()).complete("h", "m");
  }

  @Test
  void releasesAndRethrowsWhenTheEffectThrowsAnError() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.ACQUIRED);
    AssertionError failure = new AssertionError("boom");

    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    verify(store).release("h", "m");
  }

  @Test
  void keepsTheEffectFailureWhenTheReleaseFails() {
    when(store.tryAcquire("h", "m")).thenReturn(Acquisition.ACQUIRED);
    IllegalStateException failure = new IllegalStateException("boom");
    IllegalStateException releaseFailure = new IllegalStateException("release failed");
    doThrow(releaseFailure).when(store).release("h", "m");

    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure)
        .hasSuppressedException(releaseFailure);
  }

  @Test
  void runsEverythingInsideTheStoreScope() {
    IdempotencyStore scoped = mock(IdempotencyStore.class);
    when(scoped.runInScope(any())).thenReturn(Outcome.skipped());

    assertThat(new IdempotentInvoker(scoped).invoke("h", "m", () -> "x"))
        .isEqualTo(Outcome.skipped());
    verify(scoped, never()).tryAcquire("h", "m");
  }

  @Test
  void defaultScopeRunsTheWorkDirectly() {
    IdempotencyStore plain =
        new IdempotencyStore() {
          @Override
          public Acquisition tryAcquire(String handlerId, String messageId) {
            return Acquisition.ACQUIRED;
          }

          @Override
          public void complete(String handlerId, String messageId) {}

          @Override
          public void release(String handlerId, String messageId) {}
        };

    assertThat(plain.runInScope(() -> "work")).isEqualTo("work");
  }

  @Test
  void rejectsANullStore() {
    assertThatThrownBy(() -> new IdempotentInvoker(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("store");
  }
}
