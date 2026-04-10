package com.borjaglez.cqrs.introspection;

import java.util.List;
import java.util.Set;

public interface CqrsIntrospection {

  List<HandlerDescriptor> getHandlers();

  List<HandlerDescriptor> getHandlers(HandlerType type);

  List<HandlerDescriptor> getHandlersForMessage(Class<?> messageType);

  List<MiddlewareDescriptor> getMiddleware();

  Set<Class<?>> getRegisteredMessageTypes();

  int getHandlerCount(HandlerType type);
}
