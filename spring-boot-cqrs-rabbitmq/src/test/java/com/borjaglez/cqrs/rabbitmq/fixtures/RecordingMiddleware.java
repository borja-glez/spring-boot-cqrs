package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.List;
import java.util.Set;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

/** Adds its name to a shared call log and runs only in the given phases. */
public class RecordingMiddleware implements BusMiddleware {

  private final List<String> calls;
  private final String name;
  private final Set<DispatchPhase> phases;

  public RecordingMiddleware(List<String> calls, String name, DispatchPhase... phases) {
    this.calls = calls;
    this.name = name;
    this.phases = Set.of(phases);
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    calls.add(name);
    return chain.proceed(message);
  }

  @Override
  public Set<DispatchPhase> phases() {
    return phases;
  }
}
