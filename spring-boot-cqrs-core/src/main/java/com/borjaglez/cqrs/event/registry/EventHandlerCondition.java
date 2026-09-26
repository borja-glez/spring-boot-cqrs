package com.borjaglez.cqrs.event.registry;

import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;

/**
 * A parsed {@code @HandleEvent(condition = "...")} expression attached to one event handler.
 *
 * <p>Each evaluation uses a fresh {@link StandardEvaluationContext} whose root object is the event,
 * with the event also bound to the {@code #event} variable and, when available, a {@link
 * BeanResolver} for {@code @beanName} references.
 *
 * @param expression the parsed condition
 * @param beanResolver resolves {@code @beanName} references, or {@code null} when there is none
 * @param handler description of the handler method, used in error messages
 */
public record EventHandlerCondition(
    Expression expression, BeanResolver beanResolver, String handler) {

  /**
   * Evaluates the condition against {@code event}.
   *
   * @return {@code true} when the handler must run, {@code false} when it must be skipped
   * @throws EventHandlerExecutionException when the evaluation fails or does not return a boolean
   */
  public boolean matches(Event event) {
    StandardEvaluationContext context = new StandardEvaluationContext(event);
    context.setVariable("event", event);
    if (beanResolver != null) {
      context.setBeanResolver(beanResolver);
    }
    Object result;
    try {
      result = expression.getValue(context);
    } catch (RuntimeException e) {
      throw new EventHandlerExecutionException(
          "Failed to evaluate condition '"
              + expression.getExpressionString()
              + "' of event handler "
              + handler
              + " for event "
              + event.getClass().getName(),
          e);
    }
    if (result instanceof Boolean matches) {
      return matches;
    }
    throw new EventHandlerExecutionException(
        "Condition '"
            + expression.getExpressionString()
            + "' of event handler "
            + handler
            + " must evaluate to a boolean, but returned "
            + (result == null ? "null" : "a " + result.getClass().getName()),
        null);
  }
}
