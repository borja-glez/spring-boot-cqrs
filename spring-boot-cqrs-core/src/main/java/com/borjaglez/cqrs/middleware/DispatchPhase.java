package com.borjaglez.cqrs.middleware;

import java.util.List;
import java.util.Objects;

/**
 * Where a {@link BusMiddleware} runs. A middleware declares its phases with {@link
 * BusMiddleware#phases()}; each bus runs only the middlewares that declare its phase.
 *
 * <p>A message dispatched through a local bus passes the {@link #LOCAL} chain once. A message sent
 * through a remote bus (RabbitMQ, Kafka) passes the {@link #OUTBOUND} chain in the sending process,
 * before it is published, and the {@link #INBOUND} chain in the receiving process, before its
 * handler runs. A middleware that declares both {@code OUTBOUND} and {@code INBOUND} runs twice per
 * remote message, once in each process.
 */
public enum DispatchPhase {

  /**
   * In-process dispatch through {@code SpringCommandBus}, {@code SpringQueryBus} and the local
   * event bus.
   */
  LOCAL,

  /** Sending side of a remote bus, before the message is published to the broker. */
  OUTBOUND,

  /** Receiving side of a remote bus, in the consumer, before the handler runs. */
  INBOUND;

  /**
   * The middlewares of {@code middlewares} that declare this phase, in their original order.
   *
   * @param middlewares the middlewares to filter, usually every {@link BusMiddleware} bean; {@code
   *     null} is treated as empty
   * @return an unmodifiable list
   * @throws NullPointerException if a middleware returns {@code null} from {@link
   *     BusMiddleware#phases()}
   */
  public List<BusMiddleware> select(List<BusMiddleware> middlewares) {
    if (middlewares == null) {
      return List.of();
    }
    return middlewares.stream().filter(this::isDeclaredBy).toList();
  }

  private boolean isDeclaredBy(BusMiddleware middleware) {
    return Objects.requireNonNull(
            middleware.phases(), () -> middleware.getClass().getName() + ".phases() returned null")
        .contains(this);
  }
}
