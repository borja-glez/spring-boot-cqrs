package com.borjaglez.cqrs.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.retry.BackoffStrategy;
import com.borjaglez.cqrs.retry.RetryMiddleware;
import com.borjaglez.cqrs.retry.RetryPolicy;

class TransactionMiddlewareTest {

  private RecordingTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    transactionManager = new RecordingTransactionManager();
  }

  private TransactionMiddleware middleware(QueryTransactions queries) {
    return new TransactionMiddleware(transactionManager, queries);
  }

  @Test
  void commandRunsInATransactionThatCommits() throws Exception {
    boolean[] active = new boolean[1];

    Object result =
        middleware(QueryTransactions.READ_ONLY)
            .process(
                new TestCommand("x"),
                message -> {
                  active[0] = transactionManager.active;
                  return "done";
                });

    assertThat(result).isEqualTo("done");
    assertThat(active[0]).isTrue();
    assertThat(transactionManager.events).containsExactly("begin", "commit");
    assertThat(transactionManager.readOnly).containsExactly(false);
  }

  @Test
  void eventRunsInAWritableTransaction() throws Exception {
    middleware(QueryTransactions.READ_ONLY).process(new TestEvent("e"), message -> "ok");

    assertThat(transactionManager.events).containsExactly("begin", "commit");
    assertThat(transactionManager.readOnly).containsExactly(false);
  }

  @Test
  void failingHandlerRollsBack() {
    IllegalStateException failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                middleware(QueryTransactions.READ_ONLY)
                    .process(
                        new TestCommand("x"),
                        message -> {
                          throw failure;
                        }))
        .isSameAs(failure);

    assertThat(transactionManager.events).containsExactly("begin", "rollback");
  }

  @Test
  void checkedExceptionsComeOutAsTheyAre() {
    Exception failure = new Exception("checked");

    assertThatThrownBy(
            () ->
                middleware(QueryTransactions.READ_ONLY)
                    .process(
                        new TestCommand("x"),
                        message -> {
                          throw failure;
                        }))
        .isSameAs(failure);

    assertThat(transactionManager.events).containsExactly("begin", "rollback");
  }

  @Test
  void queryRunsInAReadOnlyTransaction() throws Exception {
    middleware(QueryTransactions.READ_ONLY).process(new TestQuery("q"), message -> "a");

    assertThat(transactionManager.events).containsExactly("begin", "commit");
    assertThat(transactionManager.readOnly).containsExactly(true);
  }

  @Test
  void queryIsReadWriteWhenConfigured() throws Exception {
    middleware(QueryTransactions.READ_WRITE).process(new TestQuery("q"), message -> "a");

    assertThat(transactionManager.events).containsExactly("begin", "commit");
    assertThat(transactionManager.readOnly).containsExactly(false);
  }

  @Test
  void queryRunsWithoutTransactionWhenNone() throws Exception {
    boolean[] active = new boolean[1];

    Object result =
        middleware(QueryTransactions.NONE)
            .process(
                new TestQuery("q"),
                message -> {
                  active[0] = transactionManager.active;
                  return "a";
                });

    assertThat(result).isEqualTo("a");
    assertThat(active[0]).isFalse();
    assertThat(transactionManager.events).isEmpty();
  }

  @Test
  void commandStillRunsInATransactionWhenQueriesAreNone() throws Exception {
    middleware(QueryTransactions.NONE).process(new TestCommand("x"), message -> "a");

    assertThat(transactionManager.events).containsExactly("begin", "commit");
  }

  @Test
  void joinsAnExistingTransaction() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              try {
                middleware(QueryTransactions.READ_ONLY)
                    .process(new TestCommand("x"), message -> "a");
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });

    assertThat(transactionManager.events).containsExactly("begin", "commit");
  }

  @Test
  void runsInTheDefaultPhases() {
    assertThat(middleware(QueryTransactions.READ_ONLY).phases())
        .isEqualTo(Set.of(DispatchPhase.LOCAL, DispatchPhase.INBOUND));
  }

  @Test
  void orderIsInsideRetry() {
    assertThat(TransactionMiddleware.ORDER).isGreaterThan(RetryMiddleware.ORDER);
    assertThat(middleware(QueryTransactions.READ_ONLY).getOrder())
        .isEqualTo(TransactionMiddleware.ORDER);
  }

  @Test
  void eachRetryAttemptRunsInItsOwnTransaction() throws Exception {
    RetryMiddleware retry =
        RetryMiddleware.builder()
            .defaultPolicy(
                RetryPolicy.builder()
                    .maxAttempts(2)
                    .backoff(BackoffStrategy.fixed(Duration.ZERO))
                    .build())
            .build();
    List<BusMiddleware> middlewares =
        new ArrayList<>(List.of(middleware(QueryTransactions.READ_ONLY), retry));
    AnnotationAwareOrderComparator.sort(middlewares);
    InMemoryIdempotencyStore store =
        new InMemoryIdempotencyStore(Duration.ofMinutes(1), Duration.ofMinutes(1));
    FlakyHandler handler = new FlakyHandler();
    CommandHandlerRegistry registry = new CommandHandlerRegistry();
    registry.setIdempotentInvoker(new IdempotentInvoker(store));
    registry.register(
        TestCommand.class,
        handler,
        FlakyHandler.class.getMethod("handle", TestCommand.class),
        "test.command",
        false,
        true,
        "flaky");
    SpringCommandBus bus = new SpringCommandBus(registry, middlewares);

    String result = bus.dispatchAndReceive(new TestCommand("data"));

    assertThat(result).isEqualTo("handled:data");
    assertThat(transactionManager.events).containsExactly("begin", "rollback", "begin", "commit");
    assertThat(handler.calls).isEqualTo(2);
    assertThat(handler.applied).isEqualTo(1);
  }

  public static class FlakyHandler {
    int calls;
    int applied;

    public String handle(TestCommand command) {
      if (++calls == 1) {
        throw new IllegalStateException("first attempt fails");
      }
      applied++;
      return "handled:" + command.getData();
    }
  }

  /** Records begin, commit and rollback; participates in an existing transaction. */
  private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    final List<String> events = new ArrayList<>();
    final List<Boolean> readOnly = new ArrayList<>();
    boolean active;

    @Override
    protected Object doGetTransaction() {
      return this;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
      return active;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      active = true;
      events.add("begin");
      readOnly.add(definition.isReadOnly());
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      active = false;
      events.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      active = false;
      events.add("rollback");
    }
  }
}
