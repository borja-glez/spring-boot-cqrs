package com.borjaglez.cqrs.introspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestEventHandler;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.fixtures.TestQueryHandler;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.observability.BusObservabilityInterceptor;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

class DefaultCqrsIntrospectionTest {

  private CommandHandlerRegistry commandRegistry;
  private EventHandlerRegistry eventRegistry;
  private QueryHandlerRegistry queryRegistry;

  @BeforeEach
  void setUp() {
    commandRegistry = new CommandHandlerRegistry();
    eventRegistry = new EventHandlerRegistry();
    queryRegistry = new QueryHandlerRegistry();
  }

  @Test
  void emptyRegistriesReturnEmptyCollections() {
    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlers()).isEmpty();
    assertThat(introspection.getHandlers(HandlerType.COMMAND)).isEmpty();
    assertThat(introspection.getHandlers(HandlerType.EVENT)).isEmpty();
    assertThat(introspection.getHandlers(HandlerType.QUERY)).isEmpty();
    assertThat(introspection.getMiddleware()).isEmpty();
    assertThat(introspection.getRegisteredMessageTypes()).isEmpty();
    assertThat(introspection.getHandlerCount(HandlerType.COMMAND)).isZero();
    assertThat(introspection.getHandlerCount(HandlerType.EVENT)).isZero();
    assertThat(introspection.getHandlerCount(HandlerType.QUERY)).isZero();
  }

  @Test
  void commandHandlerIsReported() throws Exception {
    registerCommand();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlers()).hasSize(1);
    HandlerDescriptor descriptor = introspection.getHandlers().get(0);
    assertThat(descriptor.messageType()).isEqualTo(TestCommand.class);
    assertThat(descriptor.handlerType()).isEqualTo(HandlerType.COMMAND);
    assertThat(descriptor.messageName()).isEqualTo("test.command");
    assertThat(descriptor.handlerBeanType()).isEqualTo(TestCommandHandler.class);
    assertThat(descriptor.requiresValidation()).isFalse();
  }

  @Test
  void commandHandlerWithValidationIsReported() throws Exception {
    TestCommandHandler handler = new TestCommandHandler();
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    commandRegistry.register(TestCommand.class, handler, method, "test.command", true);

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlers().get(0).requiresValidation()).isTrue();
  }

  @Test
  void eventHandlerIsReported() throws Exception {
    registerEvent();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlers()).hasSize(1);
    HandlerDescriptor descriptor = introspection.getHandlers().get(0);
    assertThat(descriptor.messageType()).isEqualTo(TestEvent.class);
    assertThat(descriptor.handlerType()).isEqualTo(HandlerType.EVENT);
    assertThat(descriptor.messageName()).isEqualTo("test.event");
    assertThat(descriptor.handlerBeanType()).isEqualTo(TestEventHandler.class);
    assertThat(descriptor.requiresValidation()).isFalse();
  }

  @Test
  void multipleEventHandlersForSameEvent() throws Exception {
    TestEventHandler handler1 = new TestEventHandler();
    TestEventHandler handler2 = new TestEventHandler();
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    eventRegistry.register(TestEvent.class, handler1, method, "test.event.1");
    eventRegistry.register(TestEvent.class, handler2, method, "test.event.2");

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlers(HandlerType.EVENT)).hasSize(2);
    assertThat(introspection.getHandlerCount(HandlerType.EVENT)).isEqualTo(2);
  }

  @Test
  void queryHandlerIsReported() throws Exception {
    registerQuery();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlers()).hasSize(1);
    HandlerDescriptor descriptor = introspection.getHandlers().get(0);
    assertThat(descriptor.messageType()).isEqualTo(TestQuery.class);
    assertThat(descriptor.handlerType()).isEqualTo(HandlerType.QUERY);
    assertThat(descriptor.messageName()).isEqualTo("test.query");
    assertThat(descriptor.handlerBeanType()).isEqualTo(TestQueryHandler.class);
    assertThat(descriptor.requiresValidation()).isFalse();
  }

  @Test
  void mixedHandlersAreAllReported() throws Exception {
    registerCommand();
    registerEvent();
    registerQuery();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlers()).hasSize(3);
    assertThat(introspection.getHandlers(HandlerType.COMMAND)).hasSize(1);
    assertThat(introspection.getHandlers(HandlerType.EVENT)).hasSize(1);
    assertThat(introspection.getHandlers(HandlerType.QUERY)).hasSize(1);
    assertThat(introspection.getHandlerCount(HandlerType.COMMAND)).isEqualTo(1);
    assertThat(introspection.getHandlerCount(HandlerType.EVENT)).isEqualTo(1);
    assertThat(introspection.getHandlerCount(HandlerType.QUERY)).isEqualTo(1);
  }

  @Test
  void getHandlersForMessageReturnsMatching() throws Exception {
    registerCommand();
    registerEvent();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThat(introspection.getHandlersForMessage(TestCommand.class)).hasSize(1);
    assertThat(introspection.getHandlersForMessage(TestEvent.class)).hasSize(1);
    assertThat(introspection.getHandlersForMessage(TestQuery.class)).isEmpty();
  }

  @Test
  void getRegisteredMessageTypesIsUnion() throws Exception {
    registerCommand();
    registerEvent();
    registerQuery();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    Set<Class<?>> types = introspection.getRegisteredMessageTypes();
    assertThat(types)
        .containsExactlyInAnyOrder(TestCommand.class, TestEvent.class, TestQuery.class);
  }

  @Test
  void middlewareWithOrderAnnotation() {
    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, List.of(new OrderAnnotatedMiddleware()));

    List<MiddlewareDescriptor> middleware = introspection.getMiddleware();
    assertThat(middleware).hasSize(1);
    assertThat(middleware.get(0).order()).isEqualTo(42);
    assertThat(middleware.get(0).middlewareType()).isEqualTo(OrderAnnotatedMiddleware.class);
    assertThat(middleware.get(0).isObservability()).isFalse();
  }

  @Test
  void middlewareImplementingOrdered() {
    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry,
            eventRegistry,
            queryRegistry,
            List.of(new OrderedInterfaceMiddleware()));

    List<MiddlewareDescriptor> middleware = introspection.getMiddleware();
    assertThat(middleware).hasSize(1);
    assertThat(middleware.get(0).order()).isEqualTo(10);
  }

  @Test
  void middlewareWithNoOrderDefaultsToLowestPrecedence() {
    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, List.of(new UnorderedMiddleware()));

    List<MiddlewareDescriptor> middleware = introspection.getMiddleware();
    assertThat(middleware).hasSize(1);
    assertThat(middleware.get(0).order()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
  }

  @Test
  void middlewareSortedByOrder() {
    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry,
            eventRegistry,
            queryRegistry,
            List.of(
                new UnorderedMiddleware(),
                new OrderAnnotatedMiddleware(),
                new OrderedInterfaceMiddleware()));

    List<MiddlewareDescriptor> middleware = introspection.getMiddleware();
    assertThat(middleware).hasSize(3);
    assertThat(middleware.get(0).order()).isEqualTo(10);
    assertThat(middleware.get(1).order()).isEqualTo(42);
    assertThat(middleware.get(2).order()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
  }

  @Test
  void observabilityMiddlewareDetected() {
    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry,
            eventRegistry,
            queryRegistry,
            List.of(new TestObservabilityMiddleware()));

    List<MiddlewareDescriptor> middleware = introspection.getMiddleware();
    assertThat(middleware).hasSize(1);
    assertThat(middleware.get(0).isObservability()).isTrue();
  }

  @Test
  void handlersListIsUnmodifiable() throws Exception {
    registerCommand();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThatThrownBy(() -> introspection.getHandlers().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void registeredMessageTypesIsUnmodifiable() throws Exception {
    registerCommand();

    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, Collections.emptyList());

    assertThatThrownBy(() -> introspection.getRegisteredMessageTypes().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void middlewareListIsUnmodifiable() {
    DefaultCqrsIntrospection introspection =
        new DefaultCqrsIntrospection(
            commandRegistry, eventRegistry, queryRegistry, List.of(new UnorderedMiddleware()));

    assertThatThrownBy(() -> introspection.getMiddleware().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // --- Helpers ---

  private void registerCommand() throws Exception {
    TestCommandHandler handler = new TestCommandHandler();
    Method method = TestCommandHandler.class.getMethod("handle", TestCommand.class);
    commandRegistry.register(TestCommand.class, handler, method, "test.command", false);
  }

  private void registerEvent() throws Exception {
    TestEventHandler handler = new TestEventHandler();
    Method method = TestEventHandler.class.getMethod("handle", TestEvent.class);
    eventRegistry.register(TestEvent.class, handler, method, "test.event");
  }

  private void registerQuery() throws Exception {
    TestQueryHandler handler = new TestQueryHandler();
    Method method = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    queryRegistry.register(TestQuery.class, handler, method, "test.query");
  }

  // --- Test middleware classes ---

  @Order(42)
  static class OrderAnnotatedMiddleware implements BusMiddleware {
    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      return chain.proceed(message);
    }
  }

  static class OrderedInterfaceMiddleware implements BusMiddleware, Ordered {
    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      return chain.proceed(message);
    }

    @Override
    public int getOrder() {
      return 10;
    }
  }

  static class UnorderedMiddleware implements BusMiddleware {
    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      return chain.proceed(message);
    }
  }

  static class TestObservabilityMiddleware implements BusObservabilityInterceptor {
    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      return chain.proceed(message);
    }
  }
}
