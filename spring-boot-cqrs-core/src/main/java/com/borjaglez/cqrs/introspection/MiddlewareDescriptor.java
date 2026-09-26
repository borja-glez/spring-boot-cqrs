package com.borjaglez.cqrs.introspection;

import java.util.Set;

import com.borjaglez.cqrs.middleware.DispatchPhase;

/**
 * Describes a registered middleware.
 *
 * @param middlewareType the class of the middleware
 * @param order its position in the chain; lower runs first
 * @param isObservability whether it is an observability middleware
 * @param phases where it runs: in local dispatches, on the sending side or on the receiving side of
 *     remote buses
 */
public record MiddlewareDescriptor(
    Class<?> middlewareType, int order, boolean isObservability, Set<DispatchPhase> phases) {

  public MiddlewareDescriptor {
    phases = Set.copyOf(phases);
  }

  /** Describes a middleware that runs in the default phases, {@code LOCAL} and {@code INBOUND}. */
  public MiddlewareDescriptor(Class<?> middlewareType, int order, boolean isObservability) {
    this(
        middlewareType, order, isObservability, Set.of(DispatchPhase.LOCAL, DispatchPhase.INBOUND));
  }
}
