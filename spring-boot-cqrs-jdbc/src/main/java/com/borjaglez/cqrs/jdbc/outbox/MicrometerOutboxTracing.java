package com.borjaglez.cqrs.jdbc.outbox;

import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/** {@link OutboxTracing} with Micrometer Tracing: the relay span is a child of the stored trace. */
public class MicrometerOutboxTracing implements OutboxTracing {

  public static final String SPAN_NAME = "cqrs.outbox.relay";

  private final Tracer tracer;
  private final Propagator propagator;

  public MicrometerOutboxTracing(Tracer tracer, Propagator propagator) {
    this.tracer = tracer;
    this.propagator = propagator;
  }

  @Override
  public Map<String, String> capture() {
    TraceContext current = tracer.currentTraceContext().context();
    if (current == null) {
      return Map.of();
    }
    Map<String, String> headers = new LinkedHashMap<>();
    propagator.inject(current, headers, Map::put);
    return headers;
  }

  @Override
  public void run(Map<String, String> headers, Runnable action) {
    Span.Builder builder =
        headers.isEmpty() ? tracer.spanBuilder() : propagator.extract(headers, Map::get);
    Span span = builder.name(SPAN_NAME).start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      action.run();
    } catch (RuntimeException e) {
      span.error(e);
      throw e;
    } finally {
      span.end();
    }
  }
}
