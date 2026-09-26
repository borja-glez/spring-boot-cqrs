package com.borjaglez.cqrs;

import java.util.Map;

/**
 * Helper for registries that look handlers up by the exact message class. Handlers are not matched
 * through the type hierarchy, so a message whose superclass has a handler is not handled; this
 * helper finds that superclass so the situation can be reported.
 */
public final class MessageTypeHierarchy {

  private MessageTypeHierarchy() {}

  /**
   * Returns the nearest superclass of {@code messageClass} that is a key of {@code handlers}, or
   * {@code null} when no superclass has a handler.
   */
  public static Class<?> nearestHandledSuperclass(
      Class<?> messageClass, Map<Class<?>, ?> handlers) {
    for (Class<?> type = messageClass.getSuperclass(); type != null; type = type.getSuperclass()) {
      if (handlers.containsKey(type)) {
        return type;
      }
    }
    return null;
  }
}
