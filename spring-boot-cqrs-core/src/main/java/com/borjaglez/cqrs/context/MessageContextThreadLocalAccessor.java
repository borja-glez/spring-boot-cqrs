package com.borjaglez.cqrs.context;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * Micrometer context-propagation {@link ThreadLocalAccessor} for {@link MessageContext}.
 *
 * <p>Once registered in a {@code ContextRegistry}, every executor decorated with Spring's {@code
 * ContextPropagatingTaskDecorator} (and any other context-propagation aware integration) carries
 * the caller's {@link MessageContext} to the worker thread and restores the worker's previous
 * context afterwards. MDC is not touched: {@link ContextPropagationMiddleware} mirrors the context
 * into MDC on the next dispatch.
 */
public class MessageContextThreadLocalAccessor implements ThreadLocalAccessor<MessageContext> {

  public static final String KEY = "cqrs.messageContext";

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public MessageContext getValue() {
    MessageContext current = MessageContext.current();
    return current.isEmpty() ? null : current;
  }

  @Override
  public void setValue(MessageContext value) {
    if (value == null || value.isEmpty()) {
      MessageContext.clear();
    } else {
      MessageContext.set(value);
    }
  }

  @Override
  public void setValue() {
    MessageContext.clear();
  }
}
