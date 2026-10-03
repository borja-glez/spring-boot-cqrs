package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

class MicrometerOutboxTracingTest {

  private final Tracer tracer = mock(Tracer.class);
  private final Propagator propagator = mock(Propagator.class);
  private final CurrentTraceContext current = mock(CurrentTraceContext.class);
  private final Span.Builder builder = mock(Span.Builder.class);
  private final Span span = mock(Span.class);
  private final Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
  private final MicrometerOutboxTracing tracing = new MicrometerOutboxTracing(tracer, propagator);

  @BeforeEach
  void setUp() {
    when(tracer.currentTraceContext()).thenReturn(current);
    when(builder.name(MicrometerOutboxTracing.SPAN_NAME)).thenReturn(builder);
    when(builder.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(scope);
  }

  @Test
  void captureWithoutATraceIsEmpty() {
    assertThat(tracing.capture()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void captureInjectsTheCurrentTrace() {
    TraceContext context = mock(TraceContext.class);
    when(current.context()).thenReturn(context);
    doAnswer(
            invocation -> {
              Propagator.Setter<Map<String, String>> setter = invocation.getArgument(2);
              setter.set(invocation.getArgument(1), "traceparent", "00-abc-def-01");
              return null;
            })
        .when(propagator)
        .inject(eq(context), any(), any(Propagator.Setter.class));

    assertThat(tracing.capture()).containsExactly(Map.entry("traceparent", "00-abc-def-01"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void runContinuesTheStoredTrace() {
    Map<String, String> headers = Map.of("traceparent", "00-abc-def-01");
    when(propagator.extract(eq(headers), any(Propagator.Getter.class)))
        .thenAnswer(
            invocation -> {
              Propagator.Getter<Map<String, String>> getter = invocation.getArgument(1);
              assertThat(getter.get(headers, "traceparent")).isEqualTo("00-abc-def-01");
              return builder;
            });
    AtomicBoolean ran = new AtomicBoolean();

    tracing.run(headers, () -> ran.set(true));

    assertThat(ran).isTrue();
    verify(scope).close();
    verify(span).end();
  }

  @Test
  void runWithoutAStoredTraceStartsANewOne() {
    when(tracer.spanBuilder()).thenReturn(builder);
    AtomicBoolean ran = new AtomicBoolean();

    tracing.run(Map.of(), () -> ran.set(true));

    assertThat(ran).isTrue();
    verify(span).end();
  }

  @Test
  void failureIsRecordedOnTheSpan() {
    when(tracer.spanBuilder()).thenReturn(builder);
    IllegalStateException failure = new IllegalStateException("broker down");

    assertThatThrownBy(
            () ->
                tracing.run(
                    Map.of(),
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    verify(span).error(failure);
    verify(span).end();
  }
}
