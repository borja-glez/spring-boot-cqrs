package com.borjaglez.cqrs.middleware;

import java.util.Set;

/**
 * Intercepts the messages of a bus. A middleware receives the message and the rest of the chain,
 * and decides whether and how to call {@link MiddlewareChain#proceed}.
 *
 * <p>{@link #phases()} says where it runs. By default a middleware runs in local dispatches and on
 * the receiving side of remote buses, not in the process that sends a remote message.
 */
@FunctionalInterface
public interface BusMiddleware {

  Object process(Object message, MiddlewareChain chain) throws Exception;

  /**
   * The phases in which this middleware runs. Defaults to {@link DispatchPhase#LOCAL} and {@link
   * DispatchPhase#INBOUND}; add {@link DispatchPhase#OUTBOUND} to also run in the sending process
   * of a remote bus, before the message is published.
   *
   * @return a non-null set of phases
   */
  default Set<DispatchPhase> phases() {
    return Set.of(DispatchPhase.LOCAL, DispatchPhase.INBOUND);
  }
}
