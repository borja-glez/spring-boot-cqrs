package com.borjaglez.cqrs.discovery;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import jakarta.validation.Valid;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

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

public class BeanPostProcessorHandlerDiscoverer
    implements BeanPostProcessor, BeanFactoryAware, HandlerDiscoverer {

  private static final SpelExpressionParser CONDITION_PARSER = new SpelExpressionParser();

  private final CommandHandlerRegistry commandHandlerRegistry;
  private final EventHandlerRegistry eventHandlerRegistry;
  private final QueryHandlerRegistry queryHandlerRegistry;
  private final MessageNamingStrategy namingStrategy;
  private BeanResolver beanResolver;

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

  /** Enables {@code @beanName} references in {@code @HandleEvent} conditions. */
  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanResolver = new BeanFactoryResolver(beanFactory);
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
          method -> registerCommandHandler(bean, beanName, method),
          method -> method.isAnnotationPresent(HandleCommand.class));
    }

    if (AnnotationUtils.findAnnotation(targetClass, EventHandler.class) != null) {
      ReflectionUtils.doWithMethods(
          targetClass,
          method -> registerEventHandler(bean, beanName, method),
          method -> method.isAnnotationPresent(HandleEvent.class));
    }

    if (AnnotationUtils.findAnnotation(targetClass, QueryHandler.class) != null) {
      ReflectionUtils.doWithMethods(
          targetClass,
          method -> registerQueryHandler(bean, beanName, method),
          method -> method.isAnnotationPresent(HandleQuery.class));
    }
  }

  private void registerCommandHandler(Object bean, String beanName, Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    validateSingleParameter(beanName, method, paramTypes, Command.class);
    Class<?> commandClass = paramTypes[0];
    String messageName = namingStrategy.commandName(commandClass);
    boolean requiresValidation = hasValidAnnotation(method);
    Method invocable = invocableMethod(bean, beanName, method);
    commandHandlerRegistry.register(commandClass, bean, invocable, messageName, requiresValidation);
  }

  private void registerEventHandler(Object bean, String beanName, Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    validateSingleParameter(beanName, method, paramTypes, Event.class);
    Class<?> eventClass = paramTypes[0];
    String messageName = namingStrategy.eventName(eventClass);
    Method invocable = invocableMethod(bean, beanName, method);
    Expression condition = parseCondition(beanName, method);
    eventHandlerRegistry.register(
        eventClass, bean, invocable, messageName, condition, beanResolver);
  }

  /** Parses the {@code @HandleEvent} condition once, failing startup when it is malformed. */
  private Expression parseCondition(String beanName, Method method) {
    String condition = method.getAnnotation(HandleEvent.class).condition();
    if (!StringUtils.hasText(condition)) {
      return null;
    }
    try {
      return CONDITION_PARSER.parseExpression(condition);
    } catch (ParseException e) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " on bean '"
              + beanName
              + "' has an invalid condition '"
              + condition
              + "': "
              + e.getMessage(),
          e);
    }
  }

  private void registerQueryHandler(Object bean, String beanName, Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    validateSingleParameter(beanName, method, paramTypes, Query.class);
    Class<?> queryClass = paramTypes[0];
    String messageName = namingStrategy.queryName(queryClass);
    queryHandlerRegistry.register(
        queryClass, bean, invocableMethod(bean, beanName, method), messageName);
  }

  /**
   * Returns the method to invoke on {@code bean} (which may be an AOP proxy) for the handler method
   * {@code method} declared on the target class. Annotations are still read from {@code method}.
   *
   * <p>Fails fast when the handler could not be invoked correctly: a static method has no receiver;
   * a private or final method on a proxied bean would run on the proxy instance itself (skipping
   * the advice and seeing uninitialized fields); and a JDK dynamic proxy only exposes the methods
   * of its interfaces.
   */
  private Method invocableMethod(Object bean, String beanName, Method method) {
    int modifiers = method.getModifiers();
    if (Modifier.isStatic(modifiers)) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " on bean '"
              + beanName
              + "' must not be static; make it an instance method");
    }
    if (!AopUtils.isAopProxy(bean)) {
      return method;
    }
    if (Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers)) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " on bean '"
              + beanName
              + "' is private/final and the bean is proxied (e.g. @Transactional); make it public"
              + " and non-final");
    }
    try {
      return AopUtils.selectInvocableMethod(method, bean.getClass());
    } catch (IllegalStateException e) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " on bean '"
              + beanName
              + "' cannot be invoked through the bean's proxy: "
              + e.getMessage(),
          e);
    }
  }

  private void validateSingleParameter(
      String beanName, Method method, Class<?>[] paramTypes, Class<?> expectedBaseType) {
    if (paramTypes.length != 1) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " must have exactly 1 parameter, but has "
              + paramTypes.length);
    }
    // Registries look handlers up by the exact message class, and no message instance has an
    // abstract class or an interface as its exact class, so such a handler could never run.
    // Interfaces report the abstract modifier too.
    if (Modifier.isAbstract(paramTypes[0].getModifiers())) {
      throw new IllegalStateException(
          "Handler method "
              + method.toGenericString()
              + " on bean '"
              + beanName
              + "' has parameter type "
              + paramTypes[0].getName()
              + ", which is an abstract class or an interface; handlers match the exact message"
              + " class, so it could never be invoked. Declare a concrete "
              + expectedBaseType.getSimpleName()
              + " subclass as the parameter");
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
