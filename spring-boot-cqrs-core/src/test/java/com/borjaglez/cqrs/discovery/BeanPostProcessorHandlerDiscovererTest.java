package com.borjaglez.cqrs.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
