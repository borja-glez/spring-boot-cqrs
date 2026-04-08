package com.borjaglez.cqrs.event.transactional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.fixtures.TestEvent;

class TransactionalEventBusTest {

  private final EventBus delegate = mock(EventBus.class);
  private final TransactionalEventBus eventBus = new TransactionalEventBus(delegate);
  private final TransactionTemplate transactionTemplate =
      new TransactionTemplate(new TestPlatformTransactionManager());

  @Test
  void publishImmediatelyWhenNoTransactionIsActive() {
    TestEvent event = new TestEvent("data");

    eventBus.publish(event);

    verify(delegate).publish(event);
  }

  @Test
  void publishImmediatelyWhenSynchronizationIsActiveWithoutTransaction() {
    TestEvent event = new TestEvent("data");

    TransactionSynchronizationManager.initSynchronization();
    try {
      eventBus.publish(event);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(delegate).publish(event);
  }

  @Test
  void publishAfterCommitWhenTransactionIsActive() {
    TestEvent event = new TestEvent("data");

    transactionTemplate.executeWithoutResult(
        status -> {
          eventBus.publish(event);
          verifyNoInteractions(delegate);
        });

    verify(delegate).publish(event);
  }

  @Test
  void discardQueuedEventsWhenTransactionRollsBack() {
    TestEvent event = new TestEvent("data");

    transactionTemplate.executeWithoutResult(
        status -> {
          eventBus.publish(event);
          status.setRollbackOnly();
        });

    verifyNoInteractions(delegate);
  }

  @Test
  void preserveEventOrderWithinTransaction() {
    TestEvent first = new TestEvent("one");
    TestEvent second = new TestEvent("two");
    TestEvent third = new TestEvent("three");

    transactionTemplate.executeWithoutResult(
        status -> eventBus.publish(List.of(first, second, third)));

    InOrder inOrder = inOrder(delegate);
    inOrder.verify(delegate).publish(first);
    inOrder.verify(delegate).publish(second);
    inOrder.verify(delegate).publish(third);
  }

  private static final class TestPlatformTransactionManager
      extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
