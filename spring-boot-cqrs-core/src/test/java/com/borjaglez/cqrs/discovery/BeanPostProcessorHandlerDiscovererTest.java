package com.borjaglez.cqrs.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.*;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

class BeanPostProcessorHandlerDiscovererTest {

  private CommandHandlerRegistry commandRegistry;
  private EventHandlerRegistry eventRegistry;
  private QueryHandlerRegistry queryRegistry;
  private MessageNamingStrategy namingStrategy;
  private BeanPostProcessorHandlerDiscoverer discoverer;

  @BeforeEach
  void setUp() {
    commandRegistry = new CommandHandlerRegistry();
    eventRegistry = new EventHandlerRegistry();
    queryRegistry = new QueryHandlerRegistry();
    namingStrategy = new DefaultMessageNamingStrategy("test");
    discoverer =
        new BeanPostProcessorHandlerDiscoverer(
            commandRegistry, eventRegistry, queryRegistry, namingStrategy);
  }

  @Test
  void discoversCommandHandlers() {
    TestCommandHandler handler = new TestCommandHandler();
    discoverer.postProcessAfterInitialization(handler, "testCommandHandler");

    assertThat(commandRegistry.getRegisteredCommands()).contains(TestCommand.class);

    TestCommand command = new TestCommand("discovered");
    commandRegistry.handle(command);
    assertThat(handler.getLastHandledData()).isEqualTo("discovered");
  }

  @Test
  void discoversEventHandlers() {
    TestEventHandler handler = new TestEventHandler();
    discoverer.postProcessAfterInitialization(handler, "testEventHandler");

    assertThat(eventRegistry.getRegisteredEvents()).contains(TestEvent.class);

    TestEvent event = new TestEvent("discovered");
    eventRegistry.handle(event);
    assertThat(handler.getLastHandledData()).isEqualTo("discovered");
  }

  @Test
  void discoversQueryHandlers() {
    TestQueryHandler handler = new TestQueryHandler();
    discoverer.postProcessAfterInitialization(handler, "testQueryHandler");

    assertThat(queryRegistry.getRegisteredQueries()).contains(TestQuery.class);

    Object result = queryRegistry.handle(new TestQuery("discovered"));
    assertThat(result).isEqualTo("result:discovered");
  }

  @Test
  void validatesSingleParameter() {
    // A handler with wrong number of parameters would fail. We test by creating an
    // anonymous class with an invalid handler method.
    Object invalidBean = new InvalidNoParamHandler();

    assertThatThrownBy(
            () -> discoverer.postProcessAfterInitialization(invalidBean, "invalidHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must have exactly 1 parameter");
  }

  @Test
  void validatesParameterExtendsBaseType() {
    Object invalidBean = new InvalidParamTypeHandler();

    assertThatThrownBy(
            () -> discoverer.postProcessAfterInitialization(invalidBean, "invalidHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must extend Command");
  }

  @Test
  void returnsBeanFromPostProcessAfterInitialization() {
    TestCommandHandler handler = new TestCommandHandler();
    Object result = discoverer.postProcessAfterInitialization(handler, "testCommandHandler");
    assertThat(result).isSameAs(handler);
  }

  @Test
  void validAnnotationDetection() {
    ValidatedCommandHandler handler = new ValidatedCommandHandler();
    discoverer.postProcessAfterInitialization(handler, "validatedHandler");

    var info = commandRegistry.getHandlerInfo(ValidatedCommand.class);
    assertThat(info).isPresent();
    assertThat(info.get().requiresValidation()).isTrue();
  }

  @Test
  void nonHandlerBeanIsIgnored() {
    Object plainBean = new Object();
    Object result = discoverer.postProcessAfterInitialization(plainBean, "plainBean");
    assertThat(result).isSameAs(plainBean);
    assertThat(commandRegistry.getRegisteredCommands()).isEmpty();
    assertThat(eventRegistry.getRegisteredEvents()).isEmpty();
    assertThat(queryRegistry.getRegisteredQueries()).isEmpty();
  }

  @Test
  void publicHandlerOnCglibProxyRunsThroughAdvice() {
    Collaborator collaborator = new Collaborator();
    AtomicInteger adviceCalls = new AtomicInteger();
    Object proxy = cglibProxy(new PublicCommandHandler(collaborator), adviceCalls);

    discoverer.postProcessAfterInitialization(proxy, "publicHandler");
    commandRegistry.handle(new TestCommand("x"));

    assertThat(adviceCalls).hasValue(1);
    assertThat(collaborator.touches).hasValue(1);
  }

  @Test
  void rejectsPrivateCommandHandlerOnProxiedBean() {
    Object proxy = cglibProxy(new PrivateCommandHandler(new Collaborator()), new AtomicInteger());

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(proxy, "privateHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PrivateCommandHandler.handle(")
        .hasMessageContaining("'privateHandler'")
        .hasMessageContaining("make it public and non-final");
    assertThat(commandRegistry.getRegisteredCommands()).isEmpty();
  }

  @Test
  void rejectsFinalEventHandlerOnProxiedBean() {
    Object proxy = cglibProxy(new FinalEventHandler(new Collaborator()), new AtomicInteger());

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(proxy, "finalHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FinalEventHandler.handle(")
        .hasMessageContaining("'finalHandler'")
        .hasMessageContaining("make it public and non-final");
    assertThat(eventRegistry.getRegisteredEvents()).isEmpty();
  }

  @Test
  void rejectsPrivateQueryHandlerOnProxiedBean() {
    Object proxy = cglibProxy(new PrivateQueryHandler(new Collaborator()), new AtomicInteger());

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(proxy, "privateQuery"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PrivateQueryHandler.handle(")
        .hasMessageContaining("'privateQuery'")
        .hasMessageContaining("make it public and non-final");
    assertThat(queryRegistry.getRegisteredQueries()).isEmpty();
  }

  @Test
  void rejectsStaticHandlerMethod() {
    Object bean = new StaticCommandHandler();

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(bean, "staticHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("StaticCommandHandler.handle(")
        .hasMessageContaining("'staticHandler'")
        .hasMessageContaining("must not be static");
    assertThat(commandRegistry.getRegisteredCommands()).isEmpty();
  }

  @Test
  void privateHandlerOnPlainBeanStillWorks() {
    Collaborator collaborator = new Collaborator();
    discoverer.postProcessAfterInitialization(
        new PrivateCommandHandler(collaborator), "privateHandler");

    commandRegistry.handle(new TestCommand("x"));

    assertThat(collaborator.touches).hasValue(1);
  }

  @Test
  void jdkProxyDispatchesThroughInterfaceMethod() {
    Collaborator collaborator = new Collaborator();
    AtomicInteger adviceCalls = new AtomicInteger();
    ProxyFactory factory = new ProxyFactory(new InterfaceQueryHandler(collaborator));
    factory.addAdvice(countingInterceptor(adviceCalls));
    Object proxy = factory.getProxy();
    assertThat(proxy).isNotInstanceOf(InterfaceQueryHandler.class);

    discoverer.postProcessAfterInitialization(proxy, "interfaceHandler");
    Object result = queryRegistry.handle(new TestQuery("x"));

    assertThat(result).isEqualTo("result:x");
    assertThat(adviceCalls).hasValue(1);
    assertThat(collaborator.touches).hasValue(1);
  }

  @Test
  void rejectsJdkProxyWhenHandlerMethodIsNotOnInterface() {
    ProxyFactory factory = new ProxyFactory(new NonInterfaceEventHandler());
    Object proxy = factory.getProxy();
    assertThat(proxy).isNotInstanceOf(NonInterfaceEventHandler.class);

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(proxy, "jdkHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NonInterfaceEventHandler.handle(")
        .hasMessageContaining("'jdkHandler'")
        .hasMessageContaining("interface");
    assertThat(eventRegistry.getRegisteredEvents()).isEmpty();
  }

  private static Object cglibProxy(Object target, AtomicInteger adviceCalls) {
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(countingInterceptor(adviceCalls));
    return factory.getProxy();
  }

  private static MethodInterceptor countingInterceptor(AtomicInteger adviceCalls) {
    return invocation -> {
      adviceCalls.incrementAndGet();
      return invocation.proceed();
    };
  }

  // Invalid handler fixtures for validation tests

  @com.borjaglez.cqrs.command.annotation.CommandHandler
  static class InvalidNoParamHandler {
    @com.borjaglez.cqrs.command.annotation.HandleCommand
    public void handle() {
      // no parameter - invalid
    }
  }

  @com.borjaglez.cqrs.command.annotation.CommandHandler
  static class InvalidParamTypeHandler {
    @com.borjaglez.cqrs.command.annotation.HandleCommand
    public void handle(String notACommand) {
      // wrong parameter type - invalid
    }
  }

  // Proxy fixtures

  static class Collaborator {
    final AtomicInteger touches = new AtomicInteger();

    void touch() {
      touches.incrementAndGet();
    }
  }

  @com.borjaglez.cqrs.command.annotation.CommandHandler
  static class PublicCommandHandler {
    private final Collaborator collaborator;

    PublicCommandHandler(Collaborator collaborator) {
      this.collaborator = collaborator;
    }

    @com.borjaglez.cqrs.command.annotation.HandleCommand
    public void handle(TestCommand command) {
      collaborator.touch();
    }
  }

  @com.borjaglez.cqrs.command.annotation.CommandHandler
  static class PrivateCommandHandler {
    private final Collaborator collaborator;

    PrivateCommandHandler(Collaborator collaborator) {
      this.collaborator = collaborator;
    }

    @com.borjaglez.cqrs.command.annotation.HandleCommand
    private void handle(TestCommand command) {
      collaborator.touch();
    }
  }

  @com.borjaglez.cqrs.event.annotation.EventHandler
  static class FinalEventHandler {
    private final Collaborator collaborator;

    FinalEventHandler(Collaborator collaborator) {
      this.collaborator = collaborator;
    }

    @com.borjaglez.cqrs.event.annotation.HandleEvent
    public final void handle(TestEvent event) {
      collaborator.touch();
    }
  }

  @com.borjaglez.cqrs.query.annotation.QueryHandler
  static class PrivateQueryHandler {
    private final Collaborator collaborator;

    PrivateQueryHandler(Collaborator collaborator) {
      this.collaborator = collaborator;
    }

    @com.borjaglez.cqrs.query.annotation.HandleQuery
    private String handle(TestQuery query) {
      collaborator.touch();
      return "result:" + query.getData();
    }
  }

  @com.borjaglez.cqrs.command.annotation.CommandHandler
  static class StaticCommandHandler {
    @com.borjaglez.cqrs.command.annotation.HandleCommand
    public static void handle(TestCommand command) {
      // static - invalid
    }
  }

  interface OrderQueries {
    String handle(TestQuery query);
  }

  @com.borjaglez.cqrs.query.annotation.QueryHandler
  static class InterfaceQueryHandler implements OrderQueries {
    private final Collaborator collaborator;

    InterfaceQueryHandler(Collaborator collaborator) {
      this.collaborator = collaborator;
    }

    @Override
    @com.borjaglez.cqrs.query.annotation.HandleQuery
    public String handle(TestQuery query) {
      collaborator.touch();
      return "result:" + query.getData();
    }
  }

  interface Marker {
    void unrelated();
  }

  @com.borjaglez.cqrs.event.annotation.EventHandler
  static class NonInterfaceEventHandler implements Marker {
    @Override
    public void unrelated() {
      // not a handler
    }

    @com.borjaglez.cqrs.event.annotation.HandleEvent
    public void handle(TestEvent event) {
      // not reachable through a JDK proxy
    }
  }
}
