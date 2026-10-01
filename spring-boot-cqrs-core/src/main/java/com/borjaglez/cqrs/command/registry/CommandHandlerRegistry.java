package com.borjaglez.cqrs.command.registry;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.borjaglez.cqrs.MessageNameIndex;
import com.borjaglez.cqrs.MessageTypeHierarchy;
import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandAlreadyRegisteredException;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.command.CommandNotRegisteredException;

public class CommandHandlerRegistry {

  /**
   * A registered command handler.
   *
   * @param bean the handler bean
   * @param handle the handler method
   * @param messageName the logical name of the command
   * @param requiresValidation whether the command parameter is annotated with {@code @Valid}
   * @param remote whether the handler may receive the command from a remote transport; {@code
   *     false} when it is marked {@code remote = false}
   */
  public record HandlerInfo(
      Object bean,
      MethodHandle handle,
      String messageName,
      boolean requiresValidation,
      boolean remote) {

    /** Creates the information of a remote handler. */
    public HandlerInfo(
        Object bean, MethodHandle handle, String messageName, boolean requiresValidation) {
      this(bean, handle, messageName, requiresValidation, true);
    }
  }

  private final ConcurrentHashMap<Class<?>, HandlerInfo> handlers = new ConcurrentHashMap<>();
  private final MessageNameIndex messageNames = new MessageNameIndex();

  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation) {
    register(commandClass, bean, method, messageName, requiresValidation, true);
  }

  /**
   * Registers the handler of a command.
   *
   * @param remote whether the handler may receive the command from a remote transport
   */
  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation,
      boolean remote) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info = new HandlerInfo(bean, handle, messageName, requiresValidation, remote);
    HandlerInfo existing = handlers.putIfAbsent(commandClass, info);
    if (existing != null) {
      throw new CommandAlreadyRegisteredException(commandClass);
    }
    messageNames.add(messageName, commandClass);
  }

  public Object handle(Command command) {
    HandlerInfo info = handlers.get(command.getClass());
    if (info == null) {
      throw notRegistered(command.getClass());
    }
    try {
      return info.handle().invoke(info.bean(), command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  private CommandNotRegisteredException notRegistered(Class<?> commandClass) {
    Class<?> handledSuperclass =
        MessageTypeHierarchy.nearestHandledSuperclass(commandClass, handlers);
    return handledSuperclass == null
        ? new CommandNotRegisteredException(commandClass)
        : new CommandNotRegisteredException(commandClass, handledSuperclass);
  }

  public Optional<HandlerInfo> getHandlerInfo(Class<?> commandClass) {
    return Optional.ofNullable(handlers.get(commandClass));
  }

  public Set<Class<?>> getRegisteredCommands() {
    return Collections.unmodifiableSet(handlers.keySet());
  }

  /**
   * The class of the handled command registered under {@code messageName}, the name the naming
   * strategy gives it. Empty when no handled command has that name or when two of them share it.
   * Transports use it to read an incoming message as the local class whatever class the producer
   * used.
   */
  public Optional<Class<?>> findMessageClass(String messageName) {
    return messageNames.find(messageName);
  }
}
