package com.borjaglez.cqrs.introspection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CqrsIntrospectionLoggerTest {

  @Test
  void afterSingletonsInstantiatedLogsHandlerCounts() {
    CqrsIntrospection introspection = mock(CqrsIntrospection.class);
    when(introspection.getHandlerCount(HandlerType.COMMAND)).thenReturn(2);
    when(introspection.getHandlerCount(HandlerType.EVENT)).thenReturn(3);
    when(introspection.getHandlerCount(HandlerType.QUERY)).thenReturn(1);
    when(introspection.getMiddleware()).thenReturn(Collections.emptyList());

    CqrsIntrospectionLogger logger = new CqrsIntrospectionLogger(introspection);
    logger.afterSingletonsInstantiated();

    verify(introspection).getHandlerCount(HandlerType.COMMAND);
    verify(introspection).getHandlerCount(HandlerType.EVENT);
    verify(introspection).getHandlerCount(HandlerType.QUERY);
    verify(introspection).getMiddleware();
  }

  @Test
  void afterSingletonsInstantiatedHandlesEmptyData() {
    CqrsIntrospection introspection = mock(CqrsIntrospection.class);
    when(introspection.getHandlerCount(HandlerType.COMMAND)).thenReturn(0);
    when(introspection.getHandlerCount(HandlerType.EVENT)).thenReturn(0);
    when(introspection.getHandlerCount(HandlerType.QUERY)).thenReturn(0);
    when(introspection.getMiddleware()).thenReturn(Collections.emptyList());

    CqrsIntrospectionLogger logger = new CqrsIntrospectionLogger(introspection);
    logger.afterSingletonsInstantiated();

    verify(introspection).getHandlerCount(HandlerType.COMMAND);
  }

  @Test
  void afterSingletonsInstantiatedLogsHandlerDetailsAtFineLevel() {
    Logger julLogger = Logger.getLogger(CqrsIntrospectionLogger.class.getName());
    Level originalLevel = julLogger.getLevel();
    try {
      julLogger.setLevel(Level.FINE);

      CqrsIntrospection introspection = mock(CqrsIntrospection.class);
      when(introspection.getHandlerCount(HandlerType.COMMAND)).thenReturn(1);
      when(introspection.getHandlerCount(HandlerType.EVENT)).thenReturn(0);
      when(introspection.getHandlerCount(HandlerType.QUERY)).thenReturn(0);
      when(introspection.getMiddleware()).thenReturn(Collections.emptyList());
      HandlerDescriptor descriptor =
          new HandlerDescriptor(
              Object.class, HandlerType.COMMAND, "test.command", Object.class, false);
      when(introspection.getHandlers()).thenReturn(List.of(descriptor));

      CqrsIntrospectionLogger logger = new CqrsIntrospectionLogger(introspection);
      logger.afterSingletonsInstantiated();

      verify(introspection).getHandlers();
    } finally {
      julLogger.setLevel(originalLevel);
    }
  }
}
