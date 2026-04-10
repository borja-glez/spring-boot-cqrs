package com.borjaglez.cqrs.introspection;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.SmartInitializingSingleton;

public class CqrsIntrospectionLogger implements SmartInitializingSingleton {

  private static final Logger log = Logger.getLogger(CqrsIntrospectionLogger.class.getName());

  private final CqrsIntrospection introspection;

  public CqrsIntrospectionLogger(CqrsIntrospection introspection) {
    this.introspection = introspection;
  }

  @Override
  public void afterSingletonsInstantiated() {
    log.info(
        String.format(
            "CQRS handlers registered: %d commands, %d events, %d queries, %d middleware",
            introspection.getHandlerCount(HandlerType.COMMAND),
            introspection.getHandlerCount(HandlerType.EVENT),
            introspection.getHandlerCount(HandlerType.QUERY),
            introspection.getMiddleware().size()));

    if (log.isLoggable(Level.FINE)) {
      for (HandlerDescriptor handler : introspection.getHandlers()) {
        log.fine(
            String.format(
                "  %s handler: %s -> %s",
                handler.handlerType(),
                handler.messageName(),
                handler.handlerBeanType().getSimpleName()));
      }
    }
  }
}
