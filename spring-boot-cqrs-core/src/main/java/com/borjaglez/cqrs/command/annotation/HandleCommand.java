package com.borjaglez.cqrs.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HandleCommand {

  /**
   * Whether this handler may receive its command from a remote transport such as RabbitMQ. {@code
   * false} keeps the handler local: the broker adapters neither bind nor consume the message for
   * it, and the local buses still dispatch it. Use it to keep a message annotated with {@link
   * com.borjaglez.cqrs.naming.CqrsMessage} out of the remote contract.
   *
   * @return {@code true} (the default) when the handler may be invoked remotely
   */
  boolean remote() default true;
}
