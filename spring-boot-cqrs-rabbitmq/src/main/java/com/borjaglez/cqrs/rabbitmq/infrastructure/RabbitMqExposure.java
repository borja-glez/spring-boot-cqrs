package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.util.List;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

/**
 * Which of the handled messages an application exposes over RabbitMQ, set with {@code
 * cqrs.rabbitmq.expose}. An exposed message gets a binding from the application's queue and is
 * handled when it arrives from the broker; any other message stays local: it is not bound, and the
 * consumers reject it without requeue if it reaches the queue anyway (for example through the
 * default exchange or a binding left over by an earlier version).
 *
 * <p>In both modes a handler marked {@code remote = false} is never exposed.
 */
public enum RabbitMqExposure {

  /**
   * Every handled message. Messages without {@link CqrsMessage} are routed by their kebab-case
   * simple class name, which can collide between services that share the exchange.
   */
  ALL,

  /** Only messages annotated with {@link CqrsMessage}, which form the public contract. */
  ANNOTATED;

  /**
   * Whether messages of this type may be exposed, before looking at their handlers.
   *
   * @param messageType the message class
   * @return {@code true} for every type in {@link #ALL} mode, and for types annotated with {@link
   *     CqrsMessage} in {@link #ANNOTATED} mode
   */
  public boolean exposes(Class<?> messageType) {
    return this == ALL || messageType.isAnnotationPresent(CqrsMessage.class);
  }

  /**
   * Whether a command is exposed: its type is exposed and its handler, if any, is remote.
   *
   * @param registry the registry holding the command handler
   * @param commandClass the command class
   * @return {@code true} when the command may be received from RabbitMQ
   */
  public boolean exposesCommand(CommandHandlerRegistry registry, Class<?> commandClass) {
    return exposes(commandClass)
        && registry
            .getHandlerInfo(commandClass)
            .map(CommandHandlerRegistry.HandlerInfo::remote)
            .orElse(true);
  }

  /**
   * Whether a query is exposed: its type is exposed and its handler, if any, is remote.
   *
   * @param registry the registry holding the query handler
   * @param queryClass the query class
   * @return {@code true} when the query may be received from RabbitMQ
   */
  public boolean exposesQuery(QueryHandlerRegistry registry, Class<?> queryClass) {
    return exposes(queryClass)
        && registry
            .getHandlerInfo(queryClass)
            .map(QueryHandlerRegistry.HandlerInfo::remote)
            .orElse(true);
  }

  /**
   * Whether an event is exposed: its type is exposed and, if it has handlers, at least one of them
   * is remote. Only the remote handlers run for an event received from RabbitMQ.
   *
   * @param registry the registry holding the event handlers
   * @param eventClass the event class
   * @return {@code true} when the event may be received from RabbitMQ
   */
  public boolean exposesEvent(EventHandlerRegistry registry, Class<?> eventClass) {
    List<EventHandlerRegistry.HandlerInfo> handlers = registry.getHandlerInfos(eventClass);
    return exposes(eventClass)
        && (handlers.isEmpty()
            || handlers.stream().anyMatch(EventHandlerRegistry.HandlerInfo::remote));
  }
}
