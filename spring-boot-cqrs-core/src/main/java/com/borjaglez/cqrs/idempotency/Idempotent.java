package com.borjaglez.cqrs.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a {@code @HandleCommand} or {@code @HandleEvent} method run at most once per message: a
 * redelivered command or event whose previous processing by this handler succeeded is skipped. A
 * failed processing leaves no trace, so the redelivery runs the handler again. Requires an {@link
 * IdempotencyStore} bean.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

  /**
   * Stable id of the handler in the store. Defaults to {@code <beanName>#<methodName>}; set it so
   * that renaming the bean or the method does not make the handler process recent messages again.
   */
  String name() default "";
}
