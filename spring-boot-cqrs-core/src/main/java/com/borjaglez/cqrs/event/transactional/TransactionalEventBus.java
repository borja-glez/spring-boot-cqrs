package com.borjaglez.cqrs.event.transactional;

import java.util.List;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;

/**
 * An {@link EventBus} that holds back the events published inside a transaction until it commits,
 * and drops them when it rolls back. With no transaction, events go to the delegate at once.
 *
 * <p>The events are delivered in {@link TransactionSynchronization#afterCommit()}, while the
 * committed transaction is still bound to the thread: data access that joins it ({@code
 * PROPAGATION_REQUIRED}) is never committed. {@link #isDeliveringAfterCommit()} tells such code to
 * start a transaction of its own, as {@link com.borjaglez.cqrs.transaction.TransactionMiddleware}
 * does.
 */
public class TransactionalEventBus implements EventBus {

  /** Bound while a bus delivers the events of a committed transaction that is still current. */
  private static final Object AFTER_COMMIT = new Object();

  private final EventBus delegate;
  private final Object resourceKey = new Object();

  public TransactionalEventBus(EventBus delegate) {
    this.delegate = delegate;
  }

  /**
   * Whether the transaction bound to the current thread has already committed and a {@code
   * TransactionalEventBus} is delivering its events. Work that joins that transaction is never
   * committed, so work that needs one must start a new transaction ({@code
   * PROPAGATION_REQUIRES_NEW}). Inside such a new transaction this returns {@code false} again, so
   * work nested in it joins it.
   */
  public static boolean isDeliveringAfterCommit() {
    return TransactionSynchronizationManager.hasResource(AFTER_COMMIT);
  }

  @Override
  public void publish(Event event) {
    if (!isTransactionActive()) {
      delegate.publish(event);
      return;
    }

    getOrCreateContext().add(event);
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }

  private boolean isTransactionActive() {
    return TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive();
  }

  private TransactionalEventContext getOrCreateContext() {
    TransactionalEventContext context =
        (TransactionalEventContext) TransactionSynchronizationManager.getResource(resourceKey);
    if (context != null) {
      return context;
    }

    TransactionalEventContext newContext = new TransactionalEventContext();
    TransactionSynchronizationManager.bindResource(resourceKey, newContext);
    TransactionSynchronizationManager.registerSynchronization(new Delivery(newContext));
    return newContext;
  }

  /** Delivers the events of one transaction once it commits. */
  private final class Delivery implements TransactionSynchronization {

    private final TransactionalEventContext context;
    private boolean delivering;

    private Delivery(TransactionalEventContext context) {
      this.context = context;
    }

    @Override
    public void afterCommit() {
      TransactionSynchronizationManager.bindResource(AFTER_COMMIT, Boolean.TRUE);
      delivering = true;
      try {
        // A handler of these events may publish more while the transaction is still bound:
        // they land in this same context, so keep draining until it is empty.
        for (List<Event> events = context.drain(); !events.isEmpty(); events = context.drain()) {
          events.forEach(delegate::publish);
        }
      } finally {
        delivering = false;
        TransactionSynchronizationManager.unbindResourceIfPossible(AFTER_COMMIT);
      }
    }

    // A REQUIRES_NEW transaction suspends this one: its events must go to its own context,
    // published or dropped with it, not to this one. While it runs, the committed transaction is
    // not the current one, so work inside it may join it.
    @Override
    public void suspend() {
      TransactionSynchronizationManager.unbindResource(resourceKey);
      if (delivering) {
        TransactionSynchronizationManager.unbindResource(AFTER_COMMIT);
      }
    }

    @Override
    public void resume() {
      TransactionSynchronizationManager.bindResource(resourceKey, context);
      if (delivering) {
        TransactionSynchronizationManager.bindResource(AFTER_COMMIT, Boolean.TRUE);
      }
    }

    @Override
    public void afterCompletion(int status) {
      TransactionSynchronizationManager.unbindResourceIfPossible(resourceKey);
    }
  }
}
