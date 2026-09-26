package com.borjaglez.cqrs.event.transactional;

import java.util.List;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;

public class TransactionalEventBus implements EventBus {

  private final EventBus delegate;
  private final Object resourceKey = new Object();

  public TransactionalEventBus(EventBus delegate) {
    this.delegate = delegate;
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
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            // A handler of these events may publish more while the transaction is still bound:
            // they land in this same context, so keep draining until it is empty.
            for (List<Event> events = newContext.drain();
                !events.isEmpty();
                events = newContext.drain()) {
              events.forEach(delegate::publish);
            }
          }

          // A REQUIRES_NEW transaction suspends this one: its events must go to its own context,
          // published or dropped with it, not to this one.
          @Override
          public void suspend() {
            TransactionSynchronizationManager.unbindResource(resourceKey);
          }

          @Override
          public void resume() {
            TransactionSynchronizationManager.bindResource(resourceKey, newContext);
          }

          @Override
          public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(resourceKey);
          }
        });
    return newContext;
  }
}
