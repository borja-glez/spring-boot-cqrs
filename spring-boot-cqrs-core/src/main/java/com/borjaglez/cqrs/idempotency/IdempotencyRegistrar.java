package com.borjaglez.cqrs.idempotency;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.beans.factory.SmartInitializingSingleton;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;

/**
 * Hands the {@link IdempotentInvoker} to the registries once every singleton exists, before any
 * listener container starts. The registries are created before the beans a store needs (such as a
 * {@code DataSource}) because the handler discoverer is a {@code BeanPostProcessor}, so they cannot
 * receive it in their constructor. Fails startup when a handler is {@link Idempotent} and there is
 * no invoker.
 */
public class IdempotencyRegistrar implements SmartInitializingSingleton {

  private final CommandHandlerRegistry commandHandlerRegistry;
  private final EventHandlerRegistry eventHandlerRegistry;
  private final Supplier<IdempotentInvoker> invoker;

  /**
   * @param invoker the invoker, or {@code null} when no {@link IdempotencyStore} is configured
   */
  public IdempotencyRegistrar(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      IdempotentInvoker invoker) {
    this(commandHandlerRegistry, eventHandlerRegistry, () -> invoker);
  }

  /**
   * @param invoker supplies the invoker, or {@code null} when no {@link IdempotencyStore} is
   *     configured; called once all singletons exist
   */
  public IdempotencyRegistrar(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      Supplier<IdempotentInvoker> invoker) {
    this.commandHandlerRegistry = Objects.requireNonNull(commandHandlerRegistry);
    this.eventHandlerRegistry = Objects.requireNonNull(eventHandlerRegistry);
    this.invoker = Objects.requireNonNull(invoker);
  }

  @Override
  public void afterSingletonsInstantiated() {
    IdempotentInvoker invoker = this.invoker.get();
    if (invoker != null) {
      commandHandlerRegistry.setIdempotentInvoker(invoker);
      eventHandlerRegistry.setIdempotentInvoker(invoker);
      return;
    }
    List<String> idempotentHandlers = idempotentHandlers();
    if (!idempotentHandlers.isEmpty()) {
      throw new IllegalStateException(
          "Handlers "
              + idempotentHandlers
              + " are annotated with @Idempotent but no IdempotencyStore bean is configured; add"
              + " spring-boot-cqrs-jdbc with a DataSource, set cqrs.idempotency.store=in-memory,"
              + " or define an IdempotencyStore bean");
    }
  }

  private List<String> idempotentHandlers() {
    List<String> keys = new ArrayList<>();
    for (Class<?> command : commandHandlerRegistry.getRegisteredCommands()) {
      commandHandlerRegistry
          .getHandlerInfo(command)
          .map(CommandHandlerRegistry.HandlerInfo::idempotencyKey)
          .ifPresent(keys::add);
    }
    for (Class<?> event : eventHandlerRegistry.getRegisteredEvents()) {
      for (EventHandlerRegistry.HandlerInfo info : eventHandlerRegistry.getHandlerInfos(event)) {
        if (info.idempotent()) {
          keys.add(info.idempotencyKey());
        }
      }
    }
    keys.sort(null);
    return keys;
  }
}
