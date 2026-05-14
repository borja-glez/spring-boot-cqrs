package com.borjaglez.cqrs.tracing;

import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.query.Query;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TracingMiddleware implements BusMiddleware {

  public static final String DEFAULT_OBSERVATION_NAME = "cqrs.bus.dispatch";

  private final ObservationRegistry registry;
  private final String observationName;

  public TracingMiddleware(ObservationRegistry registry, String observationName) {
    this.registry = registry;
    this.observationName = Objects.requireNonNullElse(observationName, DEFAULT_OBSERVATION_NAME);
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    return Observation.createNotStarted(observationName, registry)
        .lowCardinalityKeyValue("cqrs.message.kind", kindOf(message))
        .lowCardinalityKeyValue("cqrs.message.type", message.getClass().getSimpleName())
        .observeChecked(() -> chain.proceed(message));
  }

  private static String kindOf(Object message) {
    if (message instanceof Command) {
      return "command";
    }
    if (message instanceof Event) {
      return "event";
    }
    if (message instanceof Query) {
      return "query";
    }
    return "unknown";
  }
}
