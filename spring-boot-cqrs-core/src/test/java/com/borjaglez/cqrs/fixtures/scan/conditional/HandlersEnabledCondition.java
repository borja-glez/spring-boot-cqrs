package com.borjaglez.cqrs.fixtures.scan.conditional;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the bean factory holds {@value #ENABLING_BEAN}; needs the bean factory to decide.
 */
public class HandlersEnabledCondition implements Condition {

  public static final String ENABLING_BEAN = "conditionalHandlersEnabled";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return context.getBeanFactory().containsBean(ENABLING_BEAN);
  }
}
