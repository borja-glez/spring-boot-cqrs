package com.borjaglez.cqrs.query.registry;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.borjaglez.cqrs.MessageTypeHierarchy;
import com.borjaglez.cqrs.MethodHandleUtil;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryAlreadyRegisteredException;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.query.QueryNotRegisteredException;

public class QueryHandlerRegistry {

  /**
   * A registered query handler.
   *
   * @param bean the handler bean
   * @param handle the handler method
   * @param messageName the logical name of the query
   * @param remote whether the handler may receive the query from a remote transport; {@code false}
   *     when it is marked {@code remote = false}
   */
  public record HandlerInfo(Object bean, MethodHandle handle, String messageName, boolean remote) {

    /** Creates the information of a remote handler. */
    public HandlerInfo(Object bean, MethodHandle handle, String messageName) {
      this(bean, handle, messageName, true);
    }
  }

  private final ConcurrentHashMap<Class<?>, HandlerInfo> handlers = new ConcurrentHashMap<>();

  public void register(Class<?> queryClass, Object bean, Method method, String messageName) {
    register(queryClass, bean, method, messageName, true);
  }

  /**
   * Registers the handler of a query.
   *
   * @param remote whether the handler may receive the query from a remote transport
   */
  public void register(
      Class<?> queryClass, Object bean, Method method, String messageName, boolean remote) {
    MethodHandle handle = MethodHandleUtil.unreflect(method);
    HandlerInfo info = new HandlerInfo(bean, handle, messageName, remote);
    HandlerInfo existing = handlers.putIfAbsent(queryClass, info);
    if (existing != null) {
      throw new QueryAlreadyRegisteredException(queryClass);
    }
  }

  public Object handle(Query query) {
    HandlerInfo info = handlers.get(query.getClass());
    if (info == null) {
      throw notRegistered(query.getClass());
    }
    try {
      return info.handle().invoke(info.bean(), query);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new QueryHandlerExecutionException(e);
    }
  }

  private QueryNotRegisteredException notRegistered(Class<?> queryClass) {
    Class<?> handledSuperclass =
        MessageTypeHierarchy.nearestHandledSuperclass(queryClass, handlers);
    return handledSuperclass == null
        ? new QueryNotRegisteredException(queryClass)
        : new QueryNotRegisteredException(queryClass, handledSuperclass);
  }

  public Optional<HandlerInfo> getHandlerInfo(Class<?> queryClass) {
    return Optional.ofNullable(handlers.get(queryClass));
  }

  public Set<Class<?>> getRegisteredQueries() {
    return Collections.unmodifiableSet(handlers.keySet());
  }
}
