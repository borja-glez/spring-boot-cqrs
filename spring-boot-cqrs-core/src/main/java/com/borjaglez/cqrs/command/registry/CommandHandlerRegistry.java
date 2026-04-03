package com.borjaglez.cqrs.command.registry;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandAlreadyRegisteredException;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.command.CommandNotRegisteredException;

public class CommandHandlerRegistry {

  public record HandlerInfo(
      Object bean, MethodHandle handle, String messageName, boolean requiresValidation) {}

  private final ConcurrentHashMap<Class<?>, HandlerInfo> handlers = new ConcurrentHashMap<>();

  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info = new HandlerInfo(bean, handle, messageName, requiresValidation);
    HandlerInfo existing = handlers.putIfAbsent(commandClass, info);
    if (existing != null) {
      throw new CommandAlreadyRegisteredException(commandClass);
    }
  }

  public Object handle(Command command) {
    HandlerInfo info = handlers.get(command.getClass());
    if (info == null) {
      throw new CommandNotRegisteredException(command.getClass());
    }
    try {
      return info.handle().invoke(info.bean(), command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  public Optional<HandlerInfo> getHandlerInfo(Class<?> commandClass) {
    return Optional.ofNullable(handlers.get(commandClass));
  }

  public Set<Class<?>> getRegisteredCommands() {
    return Collections.unmodifiableSet(handlers.keySet());
  }
}
