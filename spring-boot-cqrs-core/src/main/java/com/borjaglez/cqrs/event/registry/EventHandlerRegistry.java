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

import com.borjaglez.cqrs.MessageTypeHierarchy;
import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;

public class EventHandlerRegistry {

  public record HandlerInfo(Object bean, MethodHandle handle, String messageName) {}

  private static final Log LOG = LogFactory.getLog(EventHandlerRegistry.class);

  private final ConcurrentHashMap<Class<?>, List<HandlerInfo>> handlers = new ConcurrentHashMap<>();
  private final Set<Class<?>> warnedUnhandledSubclasses = ConcurrentHashMap.newKeySet();

  public void register(Class<?> eventClass, Object bean, Method method, String messageName) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info = new HandlerInfo(bean, handle, messageName);
    handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(info);
  }

  public void handle(Event event) {
    List<HandlerInfo> handlerList = handlers.get(event.getClass());
    if (handlerList == null) {
      warnIfOnlySuperclassIsHandled(event.getClass());
      return;
    }
    for (HandlerInfo info : handlerList) {
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
