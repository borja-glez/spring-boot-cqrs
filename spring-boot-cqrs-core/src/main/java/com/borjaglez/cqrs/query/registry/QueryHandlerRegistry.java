package com.borjaglez.cqrs.query.registry;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryAlreadyRegisteredException;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.query.QueryNotRegisteredException;

public class QueryHandlerRegistry {

  public record HandlerInfo(Object bean, MethodHandle handle, String messageName) {}

  private final ConcurrentHashMap<Class<?>, HandlerInfo> handlers = new ConcurrentHashMap<>();

  public void register(Class<?> queryClass, Object bean, Method method, String messageName) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info = new HandlerInfo(bean, handle, messageName);
    HandlerInfo existing = handlers.putIfAbsent(queryClass, info);
    if (existing != null) {
      throw new QueryAlreadyRegisteredException(queryClass);
    }
  }

  public Object handle(Query query) {
    HandlerInfo info = handlers.get(query.getClass());
    if (info == null) {
      throw new QueryNotRegisteredException(query.getClass());
    }
    try {
      return info.handle().invoke(info.bean(), query);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new QueryHandlerExecutionException(e);
    }
  }

  public Set<Class<?>> getRegisteredQueries() {
    return Collections.unmodifiableSet(handlers.keySet());
  }
}
