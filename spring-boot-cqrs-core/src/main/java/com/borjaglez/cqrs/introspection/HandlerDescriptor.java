package com.borjaglez.cqrs.introspection;

/**
 * Describes a registered handler.
 *
 * @param messageType the class of the handled message
 * @param handlerType whether it handles a command, an event or a query
 * @param messageName the logical name of the message
 * @param handlerBeanType the class of the handler bean
 * @param requiresValidation whether the message is validated before the handler runs
 * @param remote whether the handler may receive the message from a remote transport; {@code false}
 *     when it is marked {@code remote = false}. Whether a remote message actually reaches it also
 *     depends on the transport (for RabbitMQ, on {@code cqrs.rabbitmq.expose}).
 */
public record HandlerDescriptor(
    Class<?> messageType,
    HandlerType handlerType,
    String messageName,
    Class<?> handlerBeanType,
    boolean requiresValidation,
    boolean remote) {

  /** Describes a remote handler. */
  public HandlerDescriptor(
      Class<?> messageType,
      HandlerType handlerType,
      String messageName,
      Class<?> handlerBeanType,
      boolean requiresValidation) {
    this(messageType, handlerType, messageName, handlerBeanType, requiresValidation, true);
  }
}
