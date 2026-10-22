package com.borjaglez.cqrs.introspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.discovery.BeanPostProcessorHandlerDiscoverer;
import com.borjaglez.cqrs.discovery.EnableCqrsHandlers;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.scan.command.ScannedCommandHandler;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

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

  @Test
  void logsAfterRefreshListingScannedHandlers() {
    Logger julLogger = Logger.getLogger(CqrsIntrospectionLogger.class.getName());
    Level originalLevel = julLogger.getLevel();
    List<String> messages = new CopyOnWriteArrayList<>();
    Handler capture =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            messages.add(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    julLogger.addHandler(capture);
    julLogger.setLevel(Level.FINE);
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(
            IntrospectionConfiguration.class, ScannedHandlersConfiguration.class)) {
      // The scanned handler is registered, and so created, after the introspection.
      assertThat(List.of(context.getBeanDefinitionNames()))
          .containsSubsequence("cqrsIntrospection", "scannedCommandHandler");
      assertThat(messages)
          .containsExactly(
              "CQRS handlers registered: 1 commands, 0 events, 0 queries, 0 middleware",
              "  COMMAND handler: scanned-command -> ScannedCommandHandler");
    } finally {
      julLogger.removeHandler(capture);
      julLogger.setLevel(originalLevel);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class IntrospectionConfiguration {

    @Bean
    static CommandHandlerRegistry commandHandlerRegistry() {
      return new CommandHandlerRegistry();
    }

    @Bean
    static EventHandlerRegistry eventHandlerRegistry() {
      return new EventHandlerRegistry();
    }

    @Bean
    static QueryHandlerRegistry queryHandlerRegistry() {
      return new QueryHandlerRegistry();
    }

    @Bean
    static BeanPostProcessorHandlerDiscoverer handlerDiscoverer(
        CommandHandlerRegistry commandHandlerRegistry,
        EventHandlerRegistry eventHandlerRegistry,
        QueryHandlerRegistry queryHandlerRegistry) {
      return new BeanPostProcessorHandlerDiscoverer(
          commandHandlerRegistry,
          eventHandlerRegistry,
          queryHandlerRegistry,
          new DefaultMessageNamingStrategy(""));
    }

    @Bean
    CqrsIntrospection cqrsIntrospection(
        CommandHandlerRegistry commandHandlerRegistry,
        EventHandlerRegistry eventHandlerRegistry,
        QueryHandlerRegistry queryHandlerRegistry) {
      return new DefaultCqrsIntrospection(
          commandHandlerRegistry, eventHandlerRegistry, queryHandlerRegistry, List.of());
    }

    @Bean
    CqrsIntrospectionLogger cqrsIntrospectionLogger(CqrsIntrospection cqrsIntrospection) {
      return new CqrsIntrospectionLogger(cqrsIntrospection);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableCqrsHandlers(basePackageClasses = ScannedCommandHandler.class)
  static class ScannedHandlersConfiguration {}
}
