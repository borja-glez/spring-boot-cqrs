package com.borjaglez.cqrs.context;

import org.springframework.core.task.TaskDecorator;

/**
 * {@link TaskDecorator} that runs each task with the {@link MessageContext} of the thread that
 * submitted it. Apply it to a {@code ThreadPoolTaskExecutor} (or any executor that accepts a task
 * decorator) to carry the context across {@code @Async} and executor hand-offs.
 *
 * @see MessageContext#wrap(Runnable)
 */
public class MessageContextTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    return MessageContext.wrap(runnable);
  }
}
