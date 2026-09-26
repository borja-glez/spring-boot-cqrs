package com.borjaglez.cqrs.kafka.infrastructure;

import com.borjaglez.cqrs.KeyedMessage;

/**
 * Reads the key a message declares through {@link KeyedMessage}. Only commands and events can
 * declare one: queries have no ordering need.
 */
public final class KafkaMessageKeys {

  private KafkaMessageKeys() {}

  /**
   * Returns the key declared by {@code message}, or {@code null} when it is a query, does not
   * implement {@link KeyedMessage}, or declares a {@code null} or blank key.
   */
  public static String declaredKey(KafkaMessageKind messageKind, Object message) {
    if (messageKind == KafkaMessageKind.QUERY || !(message instanceof KeyedMessage keyed)) {
      return null;
    }
    String key = keyed.messageKey();
    return key == null || key.isBlank() ? null : key;
  }
}
