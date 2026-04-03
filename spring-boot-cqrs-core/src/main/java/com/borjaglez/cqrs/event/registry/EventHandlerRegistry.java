package com.borjaglez.cqrs.event.registry;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;

public class EventHandlerRegistry {

  public record HandlerInfo(Object bean, MethodHandle handle, String messageName) {}

  private final ConcurrentHashMap<Class<?>, List<HandlerInfo>> handlers = new ConcurrentHashMap<>();

  public void register(Class<?> eventClass, Object bean, Method method, String messageName) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info = new HandlerInfo(bean, handle, messageName);
    handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(info);
  }

  public void handle(Event event) {
    List<HandlerInfo> handlerList = handlers.get(event.getClass());
    if (handlerList == null) {
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

  public Set<Class<?>> getRegisteredEvents() {
    return Collections.unmodifiableSet(handlers.keySet());
  }
}
