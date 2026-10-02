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
import com.borjaglez.cqrs.idempotency.DuplicateMessageException;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.Outcome;

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
   * @param handlerId the handler id in the idempotency store, or {@code null} when the handler is
   *     not {@code @Idempotent}
   */
  public record HandlerInfo(
      Object bean,
      MethodHandle handle,
      String messageName,
      boolean requiresValidation,
      boolean remote,
      String handlerId) {

    /** Creates the information of a remote handler. */
    public HandlerInfo(
        Object bean, MethodHandle handle, String messageName, boolean requiresValidation) {
      this(bean, handle, messageName, requiresValidation, true);
    }

    /** Creates the information of a handler that is not idempotent. */
    public HandlerInfo(
        Object bean,
        MethodHandle handle,
        String messageName,
        boolean requiresValidation,
        boolean remote) {
      this(bean, handle, messageName, requiresValidation, remote, null);
    }

    /** Whether the handler is {@code @Idempotent}. */
    public boolean idempotent() {
      return handlerId != null;
    }
  }

  private final ConcurrentHashMap<Class<?>, HandlerInfo> handlers = new ConcurrentHashMap<>();
  private final MessageNameIndex messageNames = new MessageNameIndex();
  private volatile IdempotentInvoker idempotentInvoker;

  /** Sets the invoker that deduplicates {@code @Idempotent} handlers. */
  public void setIdempotentInvoker(IdempotentInvoker idempotentInvoker) {
    this.idempotentInvoker = idempotentInvoker;
  }

  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation) {
    register(commandClass, bean, method, messageName, requiresValidation, true);
  }

  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation,
      boolean remote) {
    register(commandClass, bean, method, messageName, requiresValidation, remote, null);
  }

  /**
   * Registers the handler of a command.
   *
   * @param remote whether the handler may receive the command from a remote transport
   * @param handlerId the handler id in the idempotency store, or {@code null} when the handler is
   *     not {@code @Idempotent}
   */
  public void register(
      Class<?> commandClass,
      Object bean,
      Method method,
      String messageName,
      boolean requiresValidation,
      boolean remote,
      String handlerId) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info =
        new HandlerInfo(bean, handle, messageName, requiresValidation, remote, handlerId);
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
    if (!info.idempotent()) {
      return invoke(info, command);
    }
    Outcome<Object> outcome =
        invoker(info).invoke(info.handlerId(), command.getCommandId(), () -> invoke(info, command));
    if (!outcome.duplicate()) {
      return outcome.result();
    }
    if (info.handle().type().returnType() == void.class) {
      return null;
    }
    throw new DuplicateMessageException(info.handlerId(), command.getCommandId());
  }

  private static Object invoke(HandlerInfo info, Command command) {
    try {
      return info.handle().invoke(info.bean(), command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  private IdempotentInvoker invoker(HandlerInfo info) {
    IdempotentInvoker invoker = idempotentInvoker;
    if (invoker == null) {
      throw new IllegalStateException(
          "Handler "
              + info.handlerId()
              + " is @Idempotent but no IdempotencyStore is configured yet; add"
              + " spring-boot-cqrs-jdbc with a DataSource, set cqrs.idempotency.store=in-memory,"
              + " or define an IdempotencyStore bean");
    }
    return invoker;
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
