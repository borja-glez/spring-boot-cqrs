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
    Map<String, Map<String, String>> decoded = decode(captured);
    MessageContext context = MessageContext.of(decoded.getOrDefault(CONTEXT_KEY, Map.of()));
    try (MessageContext.Scope ignored = MessageContext.scope(context)) {
      tracing.run(decoded.getOrDefault(TRACE_KEY, Map.of()), action);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Map<String, String>> decode(byte[] captured) {
    if (captured == null) {
      return Map.of();
    }
    return (Map<String, Map<String, String>>) serializer.deserialize(captured, Map.class);
  }
}
