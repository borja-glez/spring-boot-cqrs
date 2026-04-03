package com.borjaglez.cqrs.discovery;

import java.lang.reflect.Method;

import jakarta.validation.Valid;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

public class BeanPostProcessorHandlerDiscoverer implements BeanPostProcessor, HandlerDiscoverer {

  private final CommandHandlerRegistry commandHandlerRegistry;
  private final EventHandlerRegistry eventHandlerRegistry;
  private final QueryHandlerRegistry queryHandlerRegistry;
  private final MessageNamingStrategy namingStrategy;

  public BeanPostProcessorHandlerDiscoverer(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      QueryHandlerRegistry queryHandlerRegistry,
      MessageNamingStrategy namingStrategy) {
    this.commandHandlerRegistry = commandHandlerRegistry;
    this.eventHandlerRegistry = eventHandlerRegistry;
    this.queryHandlerRegistry = queryHandlerRegistry;
    this.namingStrategy = namingStrategy;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    discover(bean, beanName);
    return bean;
  }

  @Override
  public void discover(Object bean, String beanName) {
    Class<?> targetClass = AopUtils.getTargetClass(bean);

    if (AnnotationUtils.findAnnotation(targetClass, CommandHandler.class) != null) {
      ReflectionUtils.doWithMethods(
          targetClass,
          method -> registerCommandHandler(bean, method),
          method -> method.isAnnotationPresent(HandleCommand.class));
    }

    if (AnnotationUtils.findAnnotation(targetClass, EventHandler.class) != null) {
      ReflectionUtils.doWithMethods(
          targetClass,
          method -> registerEventHandler(bean, method),
          method -> method.isAnnotationPresent(HandleEvent.class));
    }

    if (AnnotationUtils.findAnnotation(targetClass, QueryHandler.class) != null) {
      ReflectionUtils.doWithMethods(
          targetClass,
          method -> registerQueryHandler(bean, method),
          method -> method.isAnnotationPresent(HandleQuery.class));
    }
  }

  private void registerCommandHandler(Object bean, Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    validateSingleParameter(method, paramTypes, Command.class);
    Class<?> commandClass = paramTypes[0];
    String messageName = namingStrategy.commandName(commandClass);
    boolean requiresValidation = hasValidAnnotation(method);
    commandHandlerRegistry.register(commandClass, bean, method, messageName, requiresValidation);
  }

  private void registerEventHandler(Object bean, Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    validateSingleParameter(method, paramTypes, Event.class);
    Class<?> eventClass = paramTypes[0];
    String messageName = namingStrategy.eventName(eventClass);
    eventHandlerRegistry.register(eventClass, bean, method, messageName);
  }

  private void registerQueryHandler(Object bean, Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    validateSingleParameter(method, paramTypes, Query.class);
    Class<?> queryClass = paramTypes[0];
    String messageName = namingStrategy.queryName(queryClass);
    queryHandlerRegistry.register(queryClass, bean, method, messageName);
  }

  private void validateSingleParameter(
      Method method, Class<?>[] paramTypes, Class<?> expectedBaseType) {
    if (paramTypes.length != 1) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " must have exactly 1 parameter, but has "
              + paramTypes.length);
    }
    if (!expectedBaseType.isAssignableFrom(paramTypes[0])) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " parameter must extend "
              + expectedBaseType.getSimpleName()
              + ", but is "
              + paramTypes[0].getName());
    }
  }

  private boolean hasValidAnnotation(Method method) {
    // Called after validateSingleParameter guarantees exactly one parameter.
    return method.getParameters()[0].isAnnotationPresent(Valid.class);
  }
}
