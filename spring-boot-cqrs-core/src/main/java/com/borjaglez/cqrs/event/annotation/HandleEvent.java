package com.borjaglez.cqrs.event.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HandleEvent {

  /**
   * Spring Expression Language (SpEL) expression that decides whether the handler runs for a given
   * event. Empty (the default) means the handler always runs, without evaluating any expression.
   *
   * <p>The event is the root object, so its properties can be referenced directly ({@code newStatus
   * == 'CONFIRMED'}); it is also available as the {@code #event} variable ({@code #event.priority >
   * 5}). Beans can be referenced with {@code @beanName} when the handler is discovered inside a
   * Spring {@code BeanFactory}.
   *
   * <p>The expression is parsed when the handler is registered, so a malformed expression fails
   * startup. It must evaluate to a {@code boolean}: {@code true} invokes the handler, {@code false}
   * skips it; {@code null}, a non-boolean result or an evaluation error raise an {@code
   * EventHandlerExecutionException}.
   */
  String condition() default "";
}
