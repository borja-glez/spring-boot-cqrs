package com.borjaglez.cqrs.query.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Service;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Service
@Documented
public @interface QueryHandler {

  /**
   * Whether the handler methods of this class may receive queries from a remote transport such as
   * RabbitMQ. {@code false} keeps every handler of the class local: its messages are neither bound
   * nor consumed by the broker adapters, and are still dispatched by the local buses. A handler is
   * remote only when both this attribute and the one on its handler method are {@code true}.
   *
   * @return {@code true} (the default) when the handlers may be invoked remotely
   */
  boolean remote() default true;
}
