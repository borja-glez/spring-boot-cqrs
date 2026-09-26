package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.Set;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

/** Sender-side middleware that stops every message before it is sent. */
public class OutboundBlocker implements BusMiddleware {

  public static final String MESSAGE = "blocked on the sender";

  @Override
  public Object process(Object message, MiddlewareChain chain) {
    throw new IllegalStateException(MESSAGE);
  }

  @Override
  public Set<DispatchPhase> phases() {
    return Set.of(DispatchPhase.OUTBOUND);
  }
}
