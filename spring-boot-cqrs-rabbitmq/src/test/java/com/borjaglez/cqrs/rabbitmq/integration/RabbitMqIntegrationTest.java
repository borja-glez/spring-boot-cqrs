package com.borjaglez.cqrs.rabbitmq.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.testcontainers.DockerClientFactory;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCqrsProperties;
import com.borjaglez.cqrs.rabbitmq.fixtures.FailingCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.InternalCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalCommandHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalEvent;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalEventHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalQueryHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.SlowCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEventHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestOrder;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestOrderListQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestOrderResultCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestResult;

@SpringBootTest(
    classes = TestApplication.class,
    properties = {
      "spring.application.name=integration-test",
      "cqrs.rabbitmq.enabled=true",
      "cqrs.rabbitmq.prefix=test-cqrs",
      "spring.rabbitmq.template.reply-timeout=2000"
    })
@Import(TestContainerConfiguration.class)
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class RabbitMqIntegrationTest {

  static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Autowired private RabbitMqCqrsProperties properties;

  @Autowired private CommandHandlerRegistry commandHandlerRegistry;

  @Autowired private EventHandlerRegistry eventHandlerRegistry;

  @Autowired private QueryHandlerRegistry queryHandlerRegistry;

  @Autowired private MessageNamingStrategy messageNamingStrategy;

  @Autowired(required = false)
  private RabbitMqCommandBus rabbitMqCommandBus;

  @Autowired(required = false)
  private RabbitMqEventBus rabbitMqEventBus;

  @Autowired(required = false)
  private RabbitMqQueryBus rabbitMqQueryBus;

  @Test
  void contextShouldLoadWithRabbitMqConfiguration() {
    assertThat(properties).isNotNull();
    assertThat(properties.getPrefix()).isEqualTo("test-cqrs");
  }

  @Test
  void registriesShouldBeAvailable() {
    assertThat(commandHandlerRegistry).isNotNull();
    assertThat(eventHandlerRegistry).isNotNull();
    assertThat(queryHandlerRegistry).isNotNull();
  }

  @Test
  void rabbitMqBusesShouldBeCreated() {
    assertThat(rabbitMqCommandBus).isNotNull();
    assertThat(rabbitMqEventBus).isNotNull();
    assertThat(rabbitMqQueryBus).isNotNull();
  }

  @Test
  void shouldDispatchCommandViaRabbitMq() {
    TestCommand command = new TestCommand("integration-test-data");

    rabbitMqCommandBus.dispatch(command);

    // Command handler registered by TestCommandHandler via auto-discovery
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              // Verify the command was handled by checking the registry has our command type
              assertThat(commandHandlerRegistry.getRegisteredCommands())
                  .contains(TestCommand.class);
            });
  }

  @Test
  void shouldPublishEventViaRabbitMq() {
    TestEvent event = new TestEvent("integration-event-data");

    rabbitMqEventBus.publish(event);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(eventHandlerRegistry.getRegisteredEvents()).contains(TestEvent.class);
            });
  }

  @Test
  void shouldAskQueryViaRabbitMq() {
    TestQuery query = new TestQuery("integration-query-data");

    String result = rabbitMqQueryBus.ask(query);

    assertThat(result).isEqualTo("result:integration-query-data");
  }

  @Test
  void shouldReceiveTheCommandResultViaRabbitMq() {
    String result = rabbitMqCommandBus.dispatchAndReceive(new TestCommand("reply-data"));

    assertThat(result).isEqualTo("handled:reply-data");
  }

  @Test
  void shouldReportRemoteHandlerFailures() {
    // The error reply used to lose its correlation id, so the caller saw a null result after the
    // reply timeout instead of the failure (finding C1).
    assertThatThrownBy(() -> rabbitMqCommandBus.dispatchAndReceive(new FailingCommand("sin stock")))
        .isInstanceOfSatisfying(
            RemoteHandlerException.class,
            e -> {
              assertThat(e.getRemoteExceptionType())
                  .isEqualTo(IllegalStateException.class.getName());
              assertThat(e.getMessage()).isEqualTo("Remote handler error: sin stock");
            });
    assertThatThrownBy(() -> rabbitMqCommandBus.dispatchAndWait(new FailingCommand("otra vez")))
        .isInstanceOf(RemoteHandlerException.class);
  }

  @Test
  void shouldTellANullResultFromAMissingReply() {
    // A missing reply used to come back as a null result, so a command whose handler never
    // answered in time looked successful (finding C23).
    Object nullResult = rabbitMqCommandBus.dispatchAndReceive(new SlowCommand(0));
    assertThat(nullResult).isNull();

    assertThatThrownBy(() -> rabbitMqCommandBus.dispatchAndWait(new SlowCommand(4000)))
        .isInstanceOf(RemoteReplyTimeoutException.class);
  }

  // A reply carries only the runtime class of the result, so a generic result keeps its element
  // types only when the caller passes a ParameterizedTypeReference (finding C16).

  @Test
  void shouldAskAGenericResultWithTypedElementsGivenATypeReference() {
    List<TestOrder> orders =
        rabbitMqQueryBus.ask(
            new TestOrderListQuery(false), new ParameterizedTypeReference<List<TestOrder>>() {});

    assertThat(orders).containsExactly(new TestOrder("o-1"), new TestOrder("o-2"));
  }

  @Test
  void shouldAskAGenericResultWithMapElementsWithoutATypeReference() {
    List<Object> orders = rabbitMqQueryBus.ask(new TestOrderListQuery(false));

    assertThat(orders).containsExactly(Map.of("id", "o-1"), Map.of("id", "o-2"));
  }

  @Test
  void shouldAskAStreamToListResult() {
    // Stream.toList() answers a JDK-internal list class, which the reply names as its type.
    List<TestOrder> typed =
        rabbitMqQueryBus.ask(
            new TestOrderListQuery(true), new ParameterizedTypeReference<List<TestOrder>>() {});
    List<Object> untyped = rabbitMqQueryBus.ask(new TestOrderListQuery(true));

    assertThat(typed).containsExactly(new TestOrder("o-1"), new TestOrder("o-2"));
    assertThat(untyped).containsExactly(Map.of("id", "o-1"), Map.of("id", "o-2"));
  }

  @Test
  void shouldReceiveAGenericWrapperWithTypedContentGivenATypeReference() {
    TestResult<TestOrder> result =
        rabbitMqCommandBus.dispatchAndReceive(
            new TestOrderResultCommand("o-7"),
            new ParameterizedTypeReference<TestResult<TestOrder>>() {});

    assertThat(result.value()).isEqualTo(new TestOrder("o-7"));
  }

  @Test
  void shouldReceiveAGenericWrapperWithMapContentWithoutATypeReference() {
    TestResult<?> result = rabbitMqCommandBus.dispatchAndReceive(new TestOrderResultCommand("o-7"));

    assertThat(result.value()).isEqualTo(Map.of("id", "o-7"));
  }

  @Test
  void shouldReceiveAResultDescribedByItsRuntimeClassWithoutATypeReference() {
    TestOrder order = rabbitMqCommandBus.dispatchAndReceive(new TestOrderResultCommand("single"));

    assertThat(order).isEqualTo(new TestOrder("single"));
  }

  // Messages without @CqrsMessage, and handlers marked remote = false, stay local: they get no
  // binding, and the consumer rejects them if they reach the queue anyway (issue #69).

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired
  @Qualifier("cqrsMessageConverter")
  private MessageConverter messageConverter;

  @Autowired private LocalCommandHandler localCommandHandler;

  @Autowired private LocalQueryHandler localQueryHandler;

  @Autowired private LocalEventHandler localEventHandler;

  @Autowired private TestEventHandler testEventHandler;

  @Autowired private CommandBus commandBus;

  @Autowired private QueryBus queryBus;

  private Message toMessage(Object payload) {
    return messageConverter.toMessage(payload, new MessageProperties());
  }

  @Test
  void shouldNotHandleLocalOnlyCommandsSentOverRabbitMq() {
    String queue = "test-cqrs.integration-test.commands";

    // Through the exchange, with the routing key of the command: nothing is bound to it.
    rabbitMqCommandBus.dispatch(new LocalCommand("remote-exchange"));
    rabbitMqCommandBus.dispatch(new InternalCommand("internal-exchange"));
    // Straight to the queue through the default exchange, bypassing the bindings.
    rabbitTemplate.send("", queue, toMessage(new LocalCommand("remote-queue")));
    rabbitTemplate.send("", queue, toMessage(new InternalCommand("internal-queue")));

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(localCommandHandler.getHandled())
                    .doesNotContain(
                        "remote-exchange", "internal-exchange", "remote-queue", "internal-queue"));

    // The local bus still dispatches them.
    commandBus.dispatch(new LocalCommand("local"));
    commandBus.dispatch(new InternalCommand("internal-local"));
    assertThat(localCommandHandler.getHandled()).contains("local", "internal-local");
  }

  @Test
  void shouldAnswerALocalOnlyQuerySentStraightToTheQueueWithAnError() {
    Message reply =
        rabbitTemplate.sendAndReceive(
            "", "test-cqrs.integration-test.queries", toMessage(new LocalQuery("remote")));

    assertThat(reply).isNotNull();
    assertThat((Boolean) reply.getMessageProperties().getHeader("cqrs.error")).isTrue();
    assertThat(localQueryHandler.getHandled()).doesNotContain("remote");
    assertThat((String) queryBus.ask(new LocalQuery("local"))).isEqualTo("local:local");
  }

  @Test
  void shouldRunOnlyTheRemoteHandlersOfAnEventReceivedOverRabbitMq() {
    // The exposed event goes through the exchange, the local one straight to the queue.
    rabbitMqEventBus.publish(new TestEvent("remote-event"));
    rabbitTemplate.send(
        "", "test-cqrs.integration-test.events", toMessage(new LocalEvent("local-event")));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> assertThat(testEventHandler.getLastHandledData()).isEqualTo("remote-event"));
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () ->
                assertThat(localEventHandler.getHandled())
                    .doesNotContain("remote-event", "local-event"));
  }
}
