package com.borjaglez.cqrs.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

/**
 * Opens a {@link MessageContext} scope around the rest of the chain, generating a correlation id
 * when the context has none and {@code autoCorrelationId} is on, and mirrors the configured keys
 * into the SLF4J MDC.
 *
 * <p>Runs in every {@link DispatchPhase}. On the sending side of a remote bus it runs before the
 * message is published, so the correlation id it generates is written to the message headers and
 * the receiver continues it instead of inventing another one.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContextPropagationMiddleware implements BusMiddleware {

  private final boolean autoCorrelationId;
  private final List<String> mdcKeys;
  private final Supplier<String> correlationIdSupplier;

  public ContextPropagationMiddleware(boolean autoCorrelationId, List<String> mdcKeys) {
    this(autoCorrelationId, mdcKeys, () -> UUID.randomUUID().toString());
  }

  public ContextPropagationMiddleware(
      boolean autoCorrelationId, List<String> mdcKeys, Supplier<String> correlationIdSupplier) {
    this.autoCorrelationId = autoCorrelationId;
    this.mdcKeys =
        mdcKeys == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(mdcKeys));
    this.correlationIdSupplier =
        Objects.requireNonNull(correlationIdSupplier, "correlationIdSupplier");
  }

  /** Every phase: local dispatches, the sender and the receiver of remote messages. */
  @Override
  public Set<DispatchPhase> phases() {
    return Set.of(DispatchPhase.values());
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    MessageContext incoming = MessageContext.current();
    if (autoCorrelationId && incoming.get(MessageContext.CORRELATION_ID_KEY).isEmpty()) {
      incoming = incoming.with(MessageContext.CORRELATION_ID_KEY, correlationIdSupplier.get());
    }

    Map<String, String> previousMdc = snapshotMdc();
    try (MessageContext.Scope ignored = MessageContext.scope(incoming)) {
      applyMdc(incoming);
      return chain.proceed(message);
    } finally {
      restoreMdc(previousMdc);
    }
  }

  private Map<String, String> snapshotMdc() {
    if (mdcKeys.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> snapshot = new HashMap<>();
    for (String key : mdcKeys) {
      snapshot.put(key, MDC.get(key));
    }
    return snapshot;
  }

  private void applyMdc(MessageContext ctx) {
    Map<String, String> map = ctx.asMap();
    for (String key : mdcKeys) {
      String value = map.get(key);
      if (value != null) {
        MDC.put(key, value);
      } else {
        MDC.remove(key);
      }
    }
  }

  private void restoreMdc(Map<String, String> previous) {
    for (String key : mdcKeys) {
      String value = previous.get(key);
      if (value != null) {
        MDC.put(key, value);
      } else {
        MDC.remove(key);
      }
    }
  }

  public static Map<String, String> headerMap(MessageContext ctx, String headerPrefix) {
    if (ctx == null || ctx.isEmpty()) {
      return Collections.emptyMap();
    }
    String prefix = headerPrefix == null ? "" : headerPrefix;
    Map<String, String> headers = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : ctx.asMap().entrySet()) {
      headers.put(prefix + entry.getKey(), entry.getValue());
    }
    return headers;
  }

  public static MessageContext fromHeaders(Map<String, String> headers, String headerPrefix) {
    if (headers == null || headers.isEmpty()) {
      return MessageContext.empty();
    }
    String prefix = headerPrefix == null ? "" : headerPrefix;
    LinkedHashMap<String, String> entries = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      String key = entry.getKey();
      if (key != null && entry.getValue() != null && key.startsWith(prefix)) {
        String stripped = key.substring(prefix.length());
        if (!stripped.isEmpty()) {
          entries.put(stripped, entry.getValue());
        }
      }
    }
    return MessageContext.of(entries);
  }
}
