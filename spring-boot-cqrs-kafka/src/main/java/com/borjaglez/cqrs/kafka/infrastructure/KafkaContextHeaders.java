package com.borjaglez.cqrs.kafka.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;

/**
 * Writes the current {@link MessageContext} as Kafka record headers, one {@code prefix + key}
 * header per entry. Every message the adapter sends to a handler (published or sent with a reply)
 * goes through here, so the consumers restore the same context whatever the dispatch style.
 */
public final class KafkaContextHeaders {

  /** Default prefix of the context headers ({@code cqrs.context.header-prefix}). */
  public static final String DEFAULT_PREFIX = "cqrs.context.";

  private KafkaContextHeaders() {}

  /** Adds the current context to {@code headers}; adds nothing when there is no context. */
  public static void write(Headers headers, String prefix) {
    Map<String, String> entries =
        ContextPropagationMiddleware.headerMap(MessageContext.current(), prefix);
    for (Map.Entry<String, String> entry : entries.entrySet()) {
      headers.add(new RecordHeader(entry.getKey(), entry.getValue().getBytes(UTF_8)));
    }
  }
}
