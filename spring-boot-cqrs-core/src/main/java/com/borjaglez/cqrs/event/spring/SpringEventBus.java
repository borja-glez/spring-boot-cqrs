package com.borjaglez.cqrs.event.spring;

import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;

public class SpringEventBus implements EventBus {

  private final EventHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;

  /**
   * Creates the bus. Only the middlewares of {@code middlewares} that declare {@link
   * DispatchPhase#LOCAL} run, in the order of the list.
   */
  public SpringEventBus(EventHandlerRegistry registry, List<BusMiddleware> middlewares) {
    this.registry = registry;
    this.middlewares = DispatchPhase.LOCAL.select(middlewares);
  }

  @Override
  public void publish(Event event) {
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(
              middlewares,
              msg -> {
                registry.handle((Event) msg);
                return null;
              });
      chain.proceed(event);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new EventHandlerExecutionException(
          "Failed to publish event " + event.getClass().getName(), e);
    }
  }

  @Override
  public void publish(List<Event> events) {
    for (Event event : events) {
      publish(event);
    }
  }
}
