package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

class OutboxContextCodecTest {

  /** Captures a fixed trace and records the headers it was asked to continue. */
  static final class FakeTracing implements OutboxTracing {
    final Map<String, String> captured = new LinkedHashMap<>();
    final AtomicReference<Map<String, String>> continued = new AtomicReference<>();

    @Override
    public Map<String, String> capture() {
      return captured;
    }

    @Override
    public void run(Map<String, String> headers, Runnable action) {
      continued.set(headers);
      action.run();
    }
  }

  private final FakeTracing tracing = new FakeTracing();
  private final OutboxContextCodec codec =
      new OutboxContextCodec(new JacksonMessageSerializer(new ObjectMapper()), tracing);

  private static MessageContext.Scope correlation(String id) {
    return MessageContext.scope(MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, id));
  }

  @Test
  void nothingToCaptureGivesNull() {
    assertThat(codec.capture()).isNull();
  }

  @Test
  void restoresTheCapturedContextAndTrace() {
    tracing.captured.put("traceparent", "00-abc-def-01");
    byte[] captured;
    try (MessageContext.Scope ignored = correlation("corr-1")) {
      captured = codec.capture();
    }
    AtomicReference<String> seen = new AtomicReference<>();

    codec.runWithin(captured, () -> seen.set(MessageContext.current().correlationId()));

    assertThat(seen).hasValue("corr-1");
    assertThat(tracing.continued.get()).containsExactly(Map.entry("traceparent", "00-abc-def-01"));
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void traceAloneIsCaptured() {
    tracing.captured.put("traceparent", "00-abc-def-01");

    assertThat(codec.capture()).isNotNull();
  }

  @Test
  void nullCaptureRunsWithAnEmptyContextAndNoTrace() {
    AtomicReference<Boolean> empty = new AtomicReference<>();

    try (MessageContext.Scope ignored = correlation("outer")) {
      codec.runWithin((byte[]) null, () -> empty.set(MessageContext.current().isEmpty()));
      assertThat(MessageContext.current().correlationId()).isEqualTo("outer");
    }

    assertThat(empty).hasValue(true);
    assertThat(tracing.continued.get()).isEmpty();
  }

  @Test
  void failureOfTheActionPropagatesAndRestoresTheContext() {
    byte[] captured;
    try (MessageContext.Scope ignored = correlation("corr-2")) {
      captured = codec.capture();
    }

    assertThatThrownBy(
            () ->
                codec.runWithin(
                    captured,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .hasMessage("boom");
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void decodeOfNothingIsEmptyAndRunWithinAcceptsADecodedCapture() {
    byte[] captured;
    try (MessageContext.Scope ignored = correlation("corr-3")) {
      captured = codec.capture();
    }
    AtomicReference<String> seen = new AtomicReference<>();

    assertThat(codec.decode(null)).isEqualTo(OutboxContextCodec.Captured.NONE);
    codec.runWithin(
        codec.decode(captured), () -> seen.set(MessageContext.current().correlationId()));

    assertThat(seen).hasValue("corr-3");
  }

  @Test
  void corruptBytesFailToDecode() {
    assertThatThrownBy(() -> codec.decode("not json".getBytes()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void noopTracingCapturesNothingAndRunsTheAction() {
    AtomicReference<Boolean> ran = new AtomicReference<>(false);

    assertThat(OutboxTracing.noop().capture()).isEmpty();
    OutboxTracing.noop().run(Map.of("a", "b"), () -> ran.set(true));

    assertThat(ran).hasValue(true);
  }
}
