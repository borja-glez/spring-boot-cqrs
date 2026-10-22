package com.borjaglez.cqrs.jdbc.outbox;

import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Calls every delegate. A delegate that throws is logged and does not stop the others, nor the
 * relay: the batch already committed.
 */
final class CompositeOutboxRelayListener implements OutboxRelayListener {

  private static final Log LOG = LogFactory.getLog(OutboxRelayListener.class);

  private final List<OutboxRelayListener> delegates;

  CompositeOutboxRelayListener(List<OutboxRelayListener> delegates) {
    this.delegates = List.copyOf(delegates);
  }

  @Override
  public void published(int count) {
    each(delegate -> delegate.published(count));
  }

  @Override
  public void publishFailed() {
    each(OutboxRelayListener::publishFailed);
  }

  @Override
  public void setAside(int count) {
    each(delegate -> delegate.setAside(count));
  }

  private void each(Consumer<OutboxRelayListener> call) {
    for (OutboxRelayListener delegate : delegates) {
      try {
        call.accept(delegate);
      } catch (RuntimeException e) {
        LOG.warn("Outbox relay listener " + delegate.getClass().getName() + " failed", e);
      }
    }
  }
}
