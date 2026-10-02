package com.borjaglez.cqrs.event.registry;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;

import com.borjaglez.cqrs.MessageNameIndex;
import com.borjaglez.cqrs.MessageTypeHierarchy;
import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;

public class EventHandlerRegistry {

  /**
   * A registered event handler.
   *
   * @param bean the handler bean
   * @param handle the handler method
   * @param messageName the logical name of the event
   * @param condition the handler's {@code @HandleEvent} condition, or {@code null} when it always
   *     runs
   * @param remote whether the handler may receive the event from a remote transport; {@code false}
   *     when it is marked {@code remote = false}
   * @param handlerId the handler id in the idempotency store, or {@code null} when the handler is
   *     not {@code @Idempotent}
   */
  public record HandlerInfo(
      Object bean,
      MethodHandle handle,
      String messageName,
      EventHandlerCondition condition,
      boolean remote,
      String handlerId) {

    /** Creates the information of a remote handler without condition. */
    public HandlerInfo(Object bean, MethodHandle handle, String messageName) {
      this(bean, handle, messageName, null);
    }

    /** Creates the information of a remote handler. */
    public HandlerInfo(
        Object bean, MethodHandle handle, String messageName, EventHandlerCondition condition) {
      this(bean, handle, messageName, condition, true);
    }

    /** Creates the information of a handler that is not idempotent. */
    public HandlerInfo(
        Object bean,
        MethodHandle handle,
        String messageName,
        EventHandlerCondition condition,
        boolean remote) {
      this(bean, handle, messageName, condition, remote, null);
    }

    /** Whether the handler is {@code @Idempotent}. */
    public boolean idempotent() {
      return handlerId != null;
    }
  }

  /** An {@code @Idempotent} handler of an event class, identified by its handler id. */
  private record IdempotentHandler(Class<?> eventClass, String handlerId) {}

  private static final Log LOG = LogFactory.getLog(EventHandlerRegistry.class);

  private final ConcurrentHashMap<Class<?>, List<HandlerInfo>> handlers = new ConcurrentHashMap<>();
  private final MessageNameIndex messageNames = new MessageNameIndex();
  private final Set<Class<?>> warnedUnhandledSubclasses = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<IdempotentHandler, Method> idempotentHandlers =
      new ConcurrentHashMap<>();
  private volatile IdempotentInvoker idempotentInvoker;

  /** Sets the invoker that deduplicates {@code @Idempotent} handlers. */
  public void setIdempotentInvoker(IdempotentInvoker idempotentInvoker) {
    this.idempotentInvoker = idempotentInvoker;
  }

  public void register(Class<?> eventClass, Object bean, Method method, String messageName) {
    register(eventClass, bean, method, messageName, null, null, true);
  }

  /**
   * Registers an event handler without condition.
   *
   * @param remote whether the handler may receive the event from a remote transport
   */
  public void register(
      Class<?> eventClass, Object bean, Method method, String messageName, boolean remote) {
    register(eventClass, bean, method, messageName, null, null, remote);
  }

  /**
   * Registers an event handler that only runs when {@code condition} evaluates to {@code true}.
   *
   * @param condition the parsed condition, or {@code null} for a handler that always runs
   * @param beanResolver resolves {@code @beanName} references in the condition; may be {@code null}
   */
  public void register(
      Class<?> eventClass,
      Object bean,
      Method method,
      String messageName,
      Expression condition,
      BeanResolver beanResolver) {
    register(eventClass, bean, method, messageName, condition, beanResolver, true);
  }

  /**
   * Registers an event handler that only runs when {@code condition} evaluates to {@code true}.
   *
   * @param condition the parsed condition, or {@code null} for a handler that always runs
   * @param beanResolver resolves {@code @beanName} references in the condition; may be {@code null}
   * @param remote whether the handler may receive the event from a remote transport
   */
  public void register(
      Class<?> eventClass,
      Object bean,
      Method method,
      String messageName,
      Expression condition,
      BeanResolver beanResolver,
      boolean remote) {
    register(eventClass, bean, method, messageName, condition, beanResolver, remote, null);
  }

  /**
   * Registers an event handler that only runs when {@code condition} evaluates to {@code true}.
   *
   * @param condition the parsed condition, or {@code null} for a handler that always runs
   * @param beanResolver resolves {@code @beanName} references in the condition; may be {@code null}
   * @param remote whether the handler may receive the event from a remote transport
   * @param handlerId the handler id in the idempotency store, or {@code null} when the handler is
   *     not {@code @Idempotent}
   */
  public void register(
      Class<?> eventClass,
      Object bean,
      Method method,
      String messageName,
      Expression condition,
      BeanResolver beanResolver,
      boolean remote,
      String handlerId) {
    if (handlerId != null) {
      rejectSharedHandlerId(eventClass, method, handlerId);
    }
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    EventHandlerCondition handlerCondition =
        condition == null
            ? null
            : new EventHandlerCondition(condition, beanResolver, method.toGenericString());
    HandlerInfo info =
        new HandlerInfo(bean, handle, messageName, handlerCondition, remote, handlerId);
    handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(info);
    messageNames.add(messageName, eventClass);
  }

  /**
   * Two idempotent handlers of the same event under one handler id would share a marker, so the
   * second one would never run.
   */
  private void rejectSharedHandlerId(Class<?> eventClass, Method method, String handlerId) {
    Method previous =
        idempotentHandlers.putIfAbsent(new IdempotentHandler(eventClass, handlerId), method);
    if (previous != null) {
      throw new IllegalStateException(
          "Handlers "
              + previous.toGenericString()
              + " and "
              + method.toGenericString()
              + " of event "
              + eventClass.getName()
              + " share the @Idempotent handler id '"
              + handlerId
              + "'; give each handler a distinct @Idempotent name");
    }
  }

  /** Runs every handler of the event, local and remote. */
  public void handle(Event event) {
    handle(event, false);
  }

  /**
   * Runs the handlers of an event received from a remote transport: handlers marked {@code remote =
   * false} are skipped.
   */
  public void handleRemote(Event event) {
    handle(event, true);
  }

  private void handle(Event event, boolean remoteOnly) {
    List<HandlerInfo> handlerList = handlers.get(event.getClass());
    if (handlerList == null) {
      warnIfOnlySuperclassIsHandled(event.getClass());
      return;
    }
    for (HandlerInfo info : handlerList) {
      if (remoteOnly && !info.remote()) {
        continue;
      }
      if (info.condition() != null && !info.condition().matches(event)) {
        continue;
      }
      if (!info.idempotent()) {
        invoke(info, event);
      } else if (invoker(info)
          .invoke(info.handlerId(), event.getEventId(), () -> invoke(info, event))
          .duplicate()) {
        LOG.debug(
            "Skipping event "
                + event.getEventId()
                + " already processed by idempotent handler "
                + info.handlerId());
      }
    }
  }

  private static Object invoke(HandlerInfo info, Event event) {
    try {
      info.handle().invoke(info.bean(), event);
      return null;
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new EventHandlerExecutionException(
          "Failed to handle event " + event.getClass().getName(), e);
    }
  }

  private IdempotentInvoker invoker(HandlerInfo info) {
    IdempotentInvoker invoker = idempotentInvoker;
    if (invoker == null) {
      throw new IllegalStateException(
          "Handler "
              + info.handlerId()
              + " is @Idempotent but no IdempotencyStore is configured yet; add"
              + " spring-boot-cqrs-jdbc with a DataSource (the JDBC store needs a single DataSource"
              + " and a single, or @Primary, PlatformTransactionManager), set"
              + " cqrs.idempotency.store=in-memory, or define an IdempotencyStore bean");
    }
    return invoker;
  }

  /**
   * Handlers match the exact event class. When an event has no handler but one of its superclasses
   * does, that handler is silently skipped; log it once per event class so it does not go unseen.
   */
  private void warnIfOnlySuperclassIsHandled(Class<?> eventClass) {
    Class<?> handledSuperclass =
        MessageTypeHierarchy.nearestHandledSuperclass(eventClass, handlers);
    if (handledSuperclass != null && warnedUnhandledSubclasses.add(eventClass)) {
      LOG.warn(
          "No handler registered for event "
              + eventClass.getName()
              + "; a handler is registered for its superclass "
              + handledSuperclass.getName()
              + ", but handlers match the exact message class, so it is not called for this"
              + " event. Register a handler for "
              + eventClass.getName());
    }
  }

  public List<HandlerInfo> getHandlerInfos(Class<?> eventClass) {
    List<HandlerInfo> list = handlers.get(eventClass);
    return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
  }

  public Set<Class<?>> getRegisteredEvents() {
    return Collections.unmodifiableSet(handlers.keySet());
  }

  /**
   * The class of the handled event registered under {@code messageName}, the name the naming
   * strategy gives it. Empty when no handled event has that name or when two of them share it.
   * Transports use it to read an incoming message as the local class whatever class the producer
   * used.
   */
  public Optional<Class<?>> findMessageClass(String messageName) {
    return messageNames.find(messageName);
  }
}
