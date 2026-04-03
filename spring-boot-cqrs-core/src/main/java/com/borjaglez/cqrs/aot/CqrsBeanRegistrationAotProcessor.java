package com.borjaglez.cqrs.aot;

import java.lang.reflect.Method;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

public class CqrsBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

  @Override
  public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
    Class<?> beanClass = registeredBean.getBeanClass();

    boolean isCommandHandler =
        AnnotationUtils.findAnnotation(beanClass, CommandHandler.class) != null;
    boolean isEventHandler = AnnotationUtils.findAnnotation(beanClass, EventHandler.class) != null;
    boolean isQueryHandler = AnnotationUtils.findAnnotation(beanClass, QueryHandler.class) != null;

    if (!isCommandHandler && !isEventHandler && !isQueryHandler) {
      return null;
    }

    return (generationContext, beanRegistrationCode) -> {
      var hints = generationContext.getRuntimeHints();

      hints
          .reflection()
          .registerType(
              beanClass, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS);

      ReflectionUtils.doWithMethods(
          beanClass, method -> registerMethodParameterHints(hints, method), this::isHandlerMethod);
    };
  }

  private boolean isHandlerMethod(Method method) {
    return method.isAnnotationPresent(HandleCommand.class)
        || method.isAnnotationPresent(HandleEvent.class)
        || method.isAnnotationPresent(HandleQuery.class);
  }

  private void registerMethodParameterHints(RuntimeHints hints, Method method) {
    for (Class<?> paramType : method.getParameterTypes()) {
      hints.reflection().registerType(paramType, MemberCategory.values());
    }
  }
}
