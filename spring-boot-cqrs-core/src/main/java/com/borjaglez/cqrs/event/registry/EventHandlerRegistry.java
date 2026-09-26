package com.borjaglez.cqrs.event.registry;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.Expression;

import com.borjaglez.cqrs.MessageTypeHierarchy;
import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;

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
   */
  public record HandlerInfo(
      Object bean,
      MethodHandle handle,
      String messageName,
      EventHandlerCondition condition,
      boolean remote) {

    /** Creates the information of a remote handler without condition. */
    public HandlerInfo(Object bean, MethodHandle handle, String messageName) {
      this(bean, handle, messageName, null);
    }

    /** Creates the information of a remote handler. */
    public HandlerInfo(
        Object bean, MethodHandle handle, String messageName, EventHandlerCondition condition) {
      this(bean, handle, messageName, condition, true);
    }
  }

  private static final Log LOG = LogFactory.getLog(EventHandlerRegistry.class);

  private final ConcurrentHashMap<Class<?>, List<HandlerInfo>> handlers = new ConcurrentHashMap<>();
  private final Set<Class<?>> warnedUnhandledSubclasses = ConcurrentHashMap.newKeySet();

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
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    EventHandlerCondition handlerCondition =
        condition == null
            ? null
            : new EventHandlerCondition(condition, beanResolver, method.toGenericString());
    HandlerInfo info = new HandlerInfo(bean, handle, messageName, handlerCondition, remote);
    handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(info);
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
      try {
        info.handle().invoke(info.bean(), event);
      } catch (RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new EventHandlerExecutionException(
            "Failed to handle event " + event.getClass().getName(), e);
      }
    }
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
}
