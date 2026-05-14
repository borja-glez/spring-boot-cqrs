package com.borjaglez.cqrs.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MessageContext {

  public static final String CORRELATION_ID_KEY = "correlationId";

  private static final MessageContext EMPTY = new MessageContext(Collections.emptyMap());
  private static final ThreadLocal<MessageContext> CURRENT = ThreadLocal.withInitial(() -> EMPTY);

  private final Map<String, String> entries;

  private MessageContext(Map<String, String> entries) {
    this.entries = entries;
  }

  public static MessageContext empty() {
    return EMPTY;
  }

  public static MessageContext of(Map<String, String> entries) {
    if (entries == null || entries.isEmpty()) {
      return EMPTY;
    }
    LinkedHashMap<String, String> copy = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : entries.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    if (copy.isEmpty()) {
      return EMPTY;
    }
    return new MessageContext(Collections.unmodifiableMap(copy));
  }

  public MessageContext with(String key, String value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    LinkedHashMap<String, String> next = new LinkedHashMap<>(entries);
    next.put(key, value);
    return new MessageContext(Collections.unmodifiableMap(next));
  }

  public MessageContext merge(MessageContext other) {
    if (other == null || other.entries.isEmpty()) {
      return this;
    }
    if (entries.isEmpty()) {
      return other;
    }
    LinkedHashMap<String, String> next = new LinkedHashMap<>(entries);
    next.putAll(other.entries);
    return new MessageContext(Collections.unmodifiableMap(next));
  }

  public Optional<String> get(String key) {
    return Optional.ofNullable(entries.get(key));
  }

  public String correlationId() {
    return entries.get(CORRELATION_ID_KEY);
  }

  public Map<String, String> asMap() {
    return entries;
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public static MessageContext current() {
    return CURRENT.get();
  }

  public static Scope scope(MessageContext ctx) {
    MessageContext target = ctx == null ? EMPTY : ctx;
    MessageContext previous = CURRENT.get();
    CURRENT.set(target);
    return new Scope(previous);
  }

  public static void clear() {
    CURRENT.remove();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MessageContext other)) return false;
    return entries.equals(other.entries);
  }

  @Override
  public int hashCode() {
    return entries.hashCode();
  }

  @Override
  public String toString() {
    return "MessageContext" + entries;
  }

  public static final class Scope implements AutoCloseable {
    private final MessageContext previous;
    private boolean closed;

    private Scope(MessageContext previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      if (previous.isEmpty()) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }
}
