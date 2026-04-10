package com.borjaglez.cqrs.introspection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.observability.BusObservabilityInterceptor;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

public class DefaultCqrsIntrospection implements CqrsIntrospection {

  private final List<HandlerDescriptor> handlers;
  private final List<MiddlewareDescriptor> middleware;
  private final Set<Class<?>> registeredMessageTypes;

  public DefaultCqrsIntrospection(
      CommandHandlerRegistry commandRegistry,
      EventHandlerRegistry eventRegistry,
      QueryHandlerRegistry queryRegistry,
      List<BusMiddleware> middlewares) {
    this.handlers =
        Collections.unmodifiableList(
            buildHandlerDescriptors(commandRegistry, eventRegistry, queryRegistry));
    this.middleware = Collections.unmodifiableList(buildMiddlewareDescriptors(middlewares));
    this.registeredMessageTypes =
        Collections.unmodifiableSet(
            buildMessageTypes(commandRegistry, eventRegistry, queryRegistry));
  }

  @Override
  public List<HandlerDescriptor> getHandlers() {
    return handlers;
  }

  @Override
  public List<HandlerDescriptor> getHandlers(HandlerType type) {
    return handlers.stream().filter(h -> h.handlerType() == type).collect(Collectors.toList());
  }

  @Override
  public List<HandlerDescriptor> getHandlersForMessage(Class<?> messageType) {
    return handlers.stream()
        .filter(h -> h.messageType().equals(messageType))
        .collect(Collectors.toList());
  }

  @Override
  public List<MiddlewareDescriptor> getMiddleware() {
    return middleware;
  }

  @Override
  public Set<Class<?>> getRegisteredMessageTypes() {
    return registeredMessageTypes;
  }

  @Override
  public int getHandlerCount(HandlerType type) {
    return (int) handlers.stream().filter(h -> h.handlerType() == type).count();
  }

  private static List<HandlerDescriptor> buildHandlerDescriptors(
      CommandHandlerRegistry commandRegistry,
      EventHandlerRegistry eventRegistry,
      QueryHandlerRegistry queryRegistry) {
    List<HandlerDescriptor> result = new ArrayList<>();

    for (Class<?> commandClass : commandRegistry.getRegisteredCommands()) {
      commandRegistry
          .getHandlerInfo(commandClass)
          .ifPresent(
              info ->
                  result.add(
                      new HandlerDescriptor(
                          commandClass,
                          HandlerType.COMMAND,
                          info.messageName(),
                          info.bean().getClass(),
                          info.requiresValidation())));
    }

    for (Class<?> eventClass : eventRegistry.getRegisteredEvents()) {
      for (EventHandlerRegistry.HandlerInfo info : eventRegistry.getHandlerInfos(eventClass)) {
        result.add(
            new HandlerDescriptor(
                eventClass, HandlerType.EVENT, info.messageName(), info.bean().getClass(), false));
      }
    }

    for (Class<?> queryClass : queryRegistry.getRegisteredQueries()) {
      queryRegistry
          .getHandlerInfo(queryClass)
          .ifPresent(
              info ->
                  result.add(
                      new HandlerDescriptor(
                          queryClass,
                          HandlerType.QUERY,
                          info.messageName(),
                          info.bean().getClass(),
                          false)));
    }

    return result;
  }

  private static List<MiddlewareDescriptor> buildMiddlewareDescriptors(
      List<BusMiddleware> middlewares) {
    return middlewares.stream()
        .map(
            m ->
                new MiddlewareDescriptor(
                    m.getClass(), resolveOrder(m), m instanceof BusObservabilityInterceptor))
        .sorted(Comparator.comparingInt(MiddlewareDescriptor::order))
        .collect(Collectors.toList());
  }

  private static int resolveOrder(BusMiddleware middleware) {
    if (middleware instanceof Ordered ordered) {
      return ordered.getOrder();
    }
    Order orderAnnotation = AnnotationUtils.findAnnotation(middleware.getClass(), Order.class);
    if (orderAnnotation != null) {
      return orderAnnotation.value();
    }
    return Ordered.LOWEST_PRECEDENCE;
  }

  private static Set<Class<?>> buildMessageTypes(
      CommandHandlerRegistry commandRegistry,
      EventHandlerRegistry eventRegistry,
      QueryHandlerRegistry queryRegistry) {
    Set<Class<?>> types = new HashSet<>();
    types.addAll(commandRegistry.getRegisteredCommands());
    types.addAll(eventRegistry.getRegisteredEvents());
    types.addAll(queryRegistry.getRegisteredQueries());
    return types;
  }
}
