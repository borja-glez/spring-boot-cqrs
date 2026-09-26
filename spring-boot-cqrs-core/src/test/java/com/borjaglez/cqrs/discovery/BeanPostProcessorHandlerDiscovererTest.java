package com.borjaglez.cqrs.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

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

  @Test
  void rejectsAbstractCommandParameter() {
    Object bean = new AbstractCommandParamHandler();

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(bean, "abstractHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bean 'abstractHandler'")
        .hasMessageContaining("handle(")
        .hasMessageContaining(AbstractOrderCommand.class.getName())
        .hasMessageContaining("abstract class or an interface")
        .hasMessageContaining("exact message class");
    assertThat(commandRegistry.getRegisteredCommands()).isEmpty();
  }

  @Test
  void rejectsInterfaceEventParameter() {
    Object bean = new InterfaceEventParamHandler();

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(bean, "interfaceHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bean 'interfaceHandler'")
        .hasMessageContaining(OrderEventContract.class.getName())
        .hasMessageContaining("abstract class or an interface")
        .hasMessageContaining("exact message class");
    assertThat(eventRegistry.getRegisteredEvents()).isEmpty();
  }

  @Test
  void rejectsAbstractQueryParameter() {
    Object bean = new AbstractQueryParamHandler();

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(bean, "abstractQuery"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bean 'abstractQuery'")
        .hasMessageContaining(AbstractOrderQuery.class.getName())
        .hasMessageContaining("abstract class or an interface");
    assertThat(queryRegistry.getRegisteredQueries()).isEmpty();
  }

  @Test
  void concreteBaseClassParameterReceivesOnlyThatExactClass() {
    TestEventHandler handler = new TestEventHandler();
    discoverer.postProcessAfterInitialization(handler, "testEventHandler");

    eventRegistry.handle(new SubTestEvent("sub"));
    assertThat(handler.getLastHandledData()).isNull();

    eventRegistry.handle(new TestEvent("exact"));
    assertThat(handler.getLastHandledData()).isEqualTo("exact");
  }

  @Test
  void conditionIsParsedAtRegistration() {
    ConditionalEventHandler handler = new ConditionalEventHandler();
    discoverer.postProcessAfterInitialization(handler, "conditionalHandler");

    var infos = eventRegistry.getHandlerInfos(TestEvent.class);
    assertThat(infos).hasSize(1);
    assertThat(infos.get(0).condition()).isNotNull();
    assertThat(infos.get(0).condition().expression().getExpressionString())
        .isEqualTo("data == 'go'");

    eventRegistry.handle(new TestEvent("stop"));
    assertThat(handler.handled).isNull();
    eventRegistry.handle(new TestEvent("go"));
    assertThat(handler.handled).isEqualTo("go");
  }

  @Test
  void emptyConditionRegistersWithoutCondition() {
    discoverer.postProcessAfterInitialization(new TestEventHandler(), "testEventHandler");
    discoverer.postProcessAfterInitialization(new BlankConditionEventHandler(), "blankHandler");

    assertThat(eventRegistry.getHandlerInfos(TestEvent.class))
        .hasSize(2)
        .allSatisfy(info -> assertThat(info.condition()).isNull());
  }

  @Test
  void malformedConditionFailsWithMessageNamingTheMethod() {
    Object bean = new MalformedConditionEventHandler();

    assertThatThrownBy(() -> discoverer.postProcessAfterInitialization(bean, "malformedHandler"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MalformedConditionEventHandler.on(")
        .hasMessageContaining("'malformedHandler'")
        .hasMessageContaining("data ==")
        .hasCauseInstanceOf(org.springframework.expression.ParseException.class);
    assertThat(eventRegistry.getRegisteredEvents()).isEmpty();
  }

  @Test
  void conditionResolvesBeanReferenceThroughBeanFactory() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("featureFlags", new FeatureFlags("notify"));
    discoverer.setBeanFactory(beanFactory);
    BeanReferenceConditionEventHandler handler = new BeanReferenceConditionEventHandler();
    discoverer.postProcessAfterInitialization(handler, "beanRefHandler");

    eventRegistry.handle(new TestEvent("other"));
    assertThat(handler.handled).isNull();
    eventRegistry.handle(new TestEvent("notify"));
    assertThat(handler.handled).isEqualTo("notify");
  }

  @Test
  void beanReferenceWithoutBeanFactoryFailsAtDispatch() {
    discoverer.postProcessAfterInitialization(
        new BeanReferenceConditionEventHandler(), "beanRefHandler");

    assertThatThrownBy(() -> eventRegistry.handle(new TestEvent("notify")))
        .isInstanceOf(com.borjaglez.cqrs.event.EventHandlerExecutionException.class)
        .hasMessageContaining("@featureFlags");
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

  @Test
  void handlersAreRemoteByDefault() {
    discoverer.postProcessAfterInitialization(new TestCommandHandler(), "testCommandHandler");
    discoverer.postProcessAfterInitialization(new TestEventHandler(), "testEventHandler");
    discoverer.postProcessAfterInitialization(new TestQueryHandler(), "testQueryHandler");

    assertThat(commandRegistry.getHandlerInfo(TestCommand.class).orElseThrow().remote()).isTrue();
    assertThat(eventRegistry.getHandlerInfos(TestEvent.class))
        .singleElement()
        .extracting(EventHandlerRegistry.HandlerInfo::remote)
        .isEqualTo(true);
    assertThat(queryRegistry.getHandlerInfo(TestQuery.class).orElseThrow().remote()).isTrue();
  }

  @Test
  void handlerMethodsMarkedNotRemoteAreRegisteredAsLocal() {
    discoverer.postProcessAfterInitialization(new LocalMethodHandlers(), "localMethodHandlers");

    assertThat(commandRegistry.getHandlerInfo(TestCommand.class).orElseThrow().remote()).isFalse();
    assertThat(eventRegistry.getHandlerInfos(TestEvent.class))
        .singleElement()
        .extracting(EventHandlerRegistry.HandlerInfo::remote)
        .isEqualTo(false);
    assertThat(queryRegistry.getHandlerInfo(TestQuery.class).orElseThrow().remote()).isFalse();
  }

  @Test
  void handlerClassesMarkedNotRemoteRegisterEveryMethodAsLocal() {
    discoverer.postProcessAfterInitialization(new LocalClassHandlers(), "localClassHandlers");

    assertThat(commandRegistry.getHandlerInfo(TestCommand.class).orElseThrow().remote()).isFalse();
    assertThat(eventRegistry.getHandlerInfos(TestEvent.class))
        .singleElement()
        .extracting(EventHandlerRegistry.HandlerInfo::remote)
        .isEqualTo(false);
    assertThat(queryRegistry.getHandlerInfo(TestQuery.class).orElseThrow().remote()).isFalse();
  }

  @Test
  void localHandlersStillHandleLocalDispatch() {
    LocalMethodHandlers handler = new LocalMethodHandlers();
    discoverer.postProcessAfterInitialization(handler, "localMethodHandlers");

    commandRegistry.handle(new TestCommand("c"));
    eventRegistry.handle(new TestEvent("e"));
    Object result = queryRegistry.handle(new TestQuery("q"));

    assertThat(handler.handled).containsExactly("c", "e");
    assertThat(result).isEqualTo("local:q");
  }

  // Invalid handler fixtures for validation tests

  abstract static class AbstractOrderCommand extends com.borjaglez.cqrs.command.Command {}

  interface OrderEventContract {}

  abstract static class AbstractOrderQuery extends com.borjaglez.cqrs.query.Query {}

  static class SubTestEvent extends TestEvent {
    SubTestEvent(String data) {
      super(data);
    }
  }

  @com.borjaglez.cqrs.command.annotation.CommandHandler
  static class AbstractCommandParamHandler {
    @com.borjaglez.cqrs.command.annotation.HandleCommand
    public void handle(AbstractOrderCommand command) {
      // abstract parameter - never invocable
    }
  }

  @com.borjaglez.cqrs.event.annotation.EventHandler
  static class InterfaceEventParamHandler {
    @com.borjaglez.cqrs.event.annotation.HandleEvent
    public void on(OrderEventContract event) {
      // interface parameter - never invocable
    }
  }

  @com.borjaglez.cqrs.query.annotation.QueryHandler
  static class AbstractQueryParamHandler {
    @com.borjaglez.cqrs.query.annotation.HandleQuery
    public String handle(AbstractOrderQuery query) {
      return "never";
    }
  }

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

  // Conditional handler fixtures

  public static class FeatureFlags {
    private final String enabledFeature;

    FeatureFlags(String enabledFeature) {
      this.enabledFeature = enabledFeature;
    }

    public boolean enabled(String feature) {
      return enabledFeature.equals(feature);
    }
  }

  @com.borjaglez.cqrs.event.annotation.EventHandler
  static class ConditionalEventHandler {
    String handled;

    @com.borjaglez.cqrs.event.annotation.HandleEvent(condition = "data == 'go'")
    public void on(TestEvent event) {
      handled = event.getData();
    }
  }

  @com.borjaglez.cqrs.event.annotation.EventHandler
  static class BlankConditionEventHandler {
    @com.borjaglez.cqrs.event.annotation.HandleEvent(condition = "  ")
    public void on(TestEvent event) {
      // blank condition - always runs
    }
  }

  @com.borjaglez.cqrs.event.annotation.EventHandler
  static class MalformedConditionEventHandler {
    @com.borjaglez.cqrs.event.annotation.HandleEvent(condition = "data ==")
    public void on(TestEvent event) {
      // never registered
    }
  }

  @com.borjaglez.cqrs.event.annotation.EventHandler
  static class BeanReferenceConditionEventHandler {
    String handled;

    @com.borjaglez.cqrs.event.annotation.HandleEvent(condition = "@featureFlags.enabled(data)")
    public void on(TestEvent event) {
      handled = event.getData();
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

  // Local-only fixtures

  @com.borjaglez.cqrs.command.annotation.CommandHandler
  @com.borjaglez.cqrs.event.annotation.EventHandler
  @com.borjaglez.cqrs.query.annotation.QueryHandler
  static class LocalMethodHandlers {
    final java.util.List<String> handled = new java.util.ArrayList<>();

    @com.borjaglez.cqrs.command.annotation.HandleCommand(remote = false)
    public void handle(TestCommand command) {
      handled.add(command.getData());
    }

    @com.borjaglez.cqrs.event.annotation.HandleEvent(remote = false)
    public void on(TestEvent event) {
      handled.add(event.getData());
    }

    @com.borjaglez.cqrs.query.annotation.HandleQuery(remote = false)
    public String ask(TestQuery query) {
      return "local:" + query.getData();
    }
  }

  @com.borjaglez.cqrs.command.annotation.CommandHandler(remote = false)
  @com.borjaglez.cqrs.event.annotation.EventHandler(remote = false)
  @com.borjaglez.cqrs.query.annotation.QueryHandler(remote = false)
  static class LocalClassHandlers {
    @com.borjaglez.cqrs.command.annotation.HandleCommand
    public void handle(TestCommand command) {
      // local only
    }

    @com.borjaglez.cqrs.event.annotation.HandleEvent
    public void on(TestEvent event) {
      // local only
    }

    @com.borjaglez.cqrs.query.annotation.HandleQuery
    public String ask(TestQuery query) {
      return "local";
    }
  }
}
