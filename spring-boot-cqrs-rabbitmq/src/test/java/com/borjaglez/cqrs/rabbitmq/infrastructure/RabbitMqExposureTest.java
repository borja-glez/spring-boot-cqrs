package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.fixtures.InternalCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalCommandHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalEvent;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalEventHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalQueryHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEventHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQueryHandler;

class RabbitMqExposureTest {

  @Test
  void annotatedExposesOnlyMessagesAnnotatedWithCqrsMessage() {
    assertThat(RabbitMqExposure.ANNOTATED.exposes(TestCommand.class)).isTrue();
    assertThat(RabbitMqExposure.ANNOTATED.exposes(LocalCommand.class)).isFalse();
  }

  @Test
  void allExposesEveryMessage() {
    assertThat(RabbitMqExposure.ALL.exposes(TestCommand.class)).isTrue();
    assertThat(RabbitMqExposure.ALL.exposes(LocalCommand.class)).isTrue();
  }

  @Test
  void commandsWithALocalHandlerAreNeverExposed() throws Exception {
    CommandHandlerRegistry registry = new CommandHandlerRegistry();
    registry.register(
        TestCommand.class,
        new TestCommandHandler(),
        TestCommandHandler.class.getMethod("handle", TestCommand.class),
        "create",
        false);
    registry.register(
        LocalCommand.class,
        new LocalCommandHandler(),
        LocalCommandHandler.class.getMethod("handle", LocalCommand.class),
        "local",
        false);
    registry.register(
        InternalCommand.class,
        new LocalCommandHandler(),
        LocalCommandHandler.class.getMethod("handle", InternalCommand.class),
        "internal",
        false,
        false);

    assertThat(RabbitMqExposure.ANNOTATED.exposesCommand(registry, TestCommand.class)).isTrue();
    assertThat(RabbitMqExposure.ANNOTATED.exposesCommand(registry, LocalCommand.class)).isFalse();
    assertThat(RabbitMqExposure.ANNOTATED.exposesCommand(registry, InternalCommand.class))
        .isFalse();
    assertThat(RabbitMqExposure.ALL.exposesCommand(registry, LocalCommand.class)).isTrue();
    assertThat(RabbitMqExposure.ALL.exposesCommand(registry, InternalCommand.class)).isFalse();
  }

  @Test
  void commandsWithoutAHandlerAreExposedAccordingToTheirType() {
    CommandHandlerRegistry registry = new CommandHandlerRegistry();

    assertThat(RabbitMqExposure.ANNOTATED.exposesCommand(registry, TestCommand.class)).isTrue();
    assertThat(RabbitMqExposure.ANNOTATED.exposesCommand(registry, LocalCommand.class)).isFalse();
  }

  @Test
  void queriesWithALocalHandlerAreNeverExposed() throws Exception {
    QueryHandlerRegistry registry = new QueryHandlerRegistry();
    Method handle = TestQueryHandler.class.getMethod("handle", TestQuery.class);
    registry.register(TestQuery.class, new TestQueryHandler(), handle, "get", false);
    registry.register(
        LocalQuery.class,
        new LocalQueryHandler(),
        LocalQueryHandler.class.getMethod("handle", LocalQuery.class),
        "local");

    assertThat(RabbitMqExposure.ALL.exposesQuery(registry, TestQuery.class)).isFalse();
    assertThat(RabbitMqExposure.ALL.exposesQuery(registry, LocalQuery.class)).isTrue();
    assertThat(RabbitMqExposure.ANNOTATED.exposesQuery(registry, LocalQuery.class)).isFalse();
    assertThat(RabbitMqExposure.ANNOTATED.exposesQuery(new QueryHandlerRegistry(), TestQuery.class))
        .isTrue();
  }

  @Test
  void eventsAreExposedWhileAtLeastOneOfTheirHandlersIsRemote() throws Exception {
    EventHandlerRegistry registry = new EventHandlerRegistry();
    Method remote = TestEventHandler.class.getMethod("handle", TestEvent.class);
    Method local = LocalEventHandler.class.getMethod("on", TestEvent.class);
    registry.register(TestEvent.class, new LocalEventHandler(), local, "created", false);

    assertThat(RabbitMqExposure.ANNOTATED.exposesEvent(registry, TestEvent.class)).isFalse();

    registry.register(TestEvent.class, new TestEventHandler(), remote, "created");

    assertThat(RabbitMqExposure.ANNOTATED.exposesEvent(registry, TestEvent.class)).isTrue();
  }

  @Test
  void eventsWithoutAHandlerAreExposedAccordingToTheirType() {
    EventHandlerRegistry registry = new EventHandlerRegistry();

    assertThat(RabbitMqExposure.ANNOTATED.exposesEvent(registry, TestEvent.class)).isTrue();
    assertThat(RabbitMqExposure.ANNOTATED.exposesEvent(registry, LocalEvent.class)).isFalse();
    assertThat(RabbitMqExposure.ALL.exposesEvent(registry, LocalEvent.class)).isTrue();
  }
}
