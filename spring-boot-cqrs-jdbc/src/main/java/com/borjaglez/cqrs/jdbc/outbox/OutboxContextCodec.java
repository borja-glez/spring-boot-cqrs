package com.borjaglez.cqrs.jdbc.outbox;

import java.util.LinkedHashMap;
import java.util.Map;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.serialization.MessageSerializer;

/**
 * Stores the {@link MessageContext} and the trace of the publishing thread with an outbox row, and
 * restores both while the relay publishes it, so correlation ids and traces continue across the
 * outbox. The capture is a map {@code {"context": {...}, "trace": {...}}} written with the
 * application's {@link MessageSerializer}.
 */
public class OutboxContextCodec {

  static final String CONTEXT_KEY = "context";
  static final String TRACE_KEY = "trace";

  private final MessageSerializer serializer;
  private final OutboxTracing tracing;

  public OutboxContextCodec(MessageSerializer serializer, OutboxTracing tracing) {
    this.serializer = serializer;
    this.tracing = tracing;
  }

  /** The current message context and trace; {@code null} when both are empty. */
  public byte[] capture() {
    Map<String, String> context = MessageContext.current().asMap();
    Map<String, String> trace = tracing.capture();
    if (context.isEmpty() && trace.isEmpty()) {
      return null;
    }
    Map<String, Map<String, String>> captured = new LinkedHashMap<>();
    captured.put(CONTEXT_KEY, context);
    captured.put(TRACE_KEY, trace);
    return serializer.serialize(captured);
  }

  /** Runs {@code action} with the captured context restored and inside the captured trace. */
  public void runWithin(byte[] captured, Runnable action) {
    runWithin(decode(captured), action);
  }

  /**
   * Same as {@link #runWithin(byte[], Runnable)} for an already {@linkplain #decode decoded}
   * capture.
   */
  public void runWithin(Captured captured, Runnable action) {
    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.of(captured.context()))) {
      tracing.run(captured.trace(), action);
    }
  }

  /**
   * Reads a stored capture; {@code null} gives {@link Captured#NONE}. Fails on unreadable bytes.
   */
  @SuppressWarnings("unchecked")
  public Captured decode(byte[] captured) {
    if (captured == null) {
      return Captured.NONE;
    }
    Map<String, Map<String, String>> decoded =
        (Map<String, Map<String, String>>) serializer.deserialize(captured, Map.class);
    return new Captured(
        decoded.getOrDefault(CONTEXT_KEY, Map.of()), decoded.getOrDefault(TRACE_KEY, Map.of()));
  }

  /** A decoded capture: the message context entries and the trace headers. */
  public record Captured(Map<String, String> context, Map<String, String> trace) {

    public static final Captured NONE = new Captured(Map.of(), Map.of());
  }
}
