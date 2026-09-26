package com.borjaglez.cqrs.fixtures;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

/** Middleware that records the messages it sees and runs only in the given phases. */
public class RecordingMiddleware implements BusMiddleware {

  private final Set<DispatchPhase> phases;
  private final List<Object> seen = new ArrayList<>();

  public RecordingMiddleware(DispatchPhase... phases) {
    this.phases = Set.of(phases);
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    seen.add(message);
    return chain.proceed(message);
  }

  @Override
  public Set<DispatchPhase> phases() {
    return phases;
  }

  public List<Object> seen() {
    return seen;
  }
}
