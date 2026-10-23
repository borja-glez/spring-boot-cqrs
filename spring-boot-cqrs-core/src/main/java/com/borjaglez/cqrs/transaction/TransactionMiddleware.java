package com.borjaglez.cqrs.transaction;

import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.query.Query;

/**
 * Runs the rest of the chain, and so the handler, in a transaction, so handlers need no
 * {@code @Transactional} of their own (for instance in hexagonal applications whose handlers carry
 * no Spring annotations).
 *
 * <p>Commands and events run with {@code PROPAGATION_REQUIRED}: they join a transaction already in
 * progress or start one. Queries follow {@link QueryTransactions}. A failure rolls the transaction
 * back and is rethrown as is; a checked exception is not wrapped further than the bus already does.
 *
 * <p>Ordered at {@link #ORDER}, inside {@link com.borjaglez.cqrs.retry.RetryMiddleware}, so each
 * retry attempt runs in its own transaction. The chain includes the {@code @Idempotent} invoker, so
 * the idempotency marker is written in the same transaction as the handler.
 */
public class TransactionMiddleware implements BusMiddleware, Ordered {

  /** {@code Ordered.LOWEST_PRECEDENCE - 50}. */
  public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 50;

  private final TransactionTemplate writable;
  private final TransactionTemplate readOnly;
  private final QueryTransactions queries;

  public TransactionMiddleware(
      PlatformTransactionManager transactionManager, QueryTransactions queries) {
    Objects.requireNonNull(transactionManager, "transactionManager");
    this.queries = Objects.requireNonNull(queries, "queries");
    this.writable = new TransactionTemplate(transactionManager);
    this.readOnly = new TransactionTemplate(transactionManager);
    this.readOnly.setReadOnly(true);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    TransactionTemplate template = templateFor(message);
    if (template == null) {
      return chain.proceed(message);
    }
    try {
      return template.execute(
          status -> {
            try {
              return chain.proceed(message);
            } catch (RuntimeException | Error e) {
              throw e;
            } catch (Exception e) {
              throw new CheckedFailure(e);
            }
          });
    } catch (CheckedFailure failure) {
      throw failure.checked;
    }
  }

  private TransactionTemplate templateFor(Object message) {
    if (!(message instanceof Query)) {
      return writable;
    }
    return switch (queries) {
      case READ_ONLY -> readOnly;
      case READ_WRITE -> writable;
      case NONE -> null;
    };
  }

  /** Carries a checked exception through the transaction callback, which rolls back on it. */
  private static final class CheckedFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Exception checked;

    private CheckedFailure(Exception checked) {
      super(checked);
      this.checked = checked;
    }
  }
}
