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
            newContext.snapshot().forEach(delegate::publish);
          }

          @Override
          public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(resourceKey);
          }
        });
    return newContext;
  }
}
