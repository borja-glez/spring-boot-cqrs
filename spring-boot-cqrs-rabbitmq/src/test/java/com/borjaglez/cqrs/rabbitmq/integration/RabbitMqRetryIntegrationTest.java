package com.borjaglez.cqrs.rabbitmq.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqCommandConsumer;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;
import com.borjaglez.cqrs.rabbitmq.infrastructure.DefaultRabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

/**
 * Two applications subscribed to the same command on a real broker: a failure in one of them must
 * be retried in that application only, and dead-lettered there once the attempts are exhausted.
 */
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class RabbitMqRetryIntegrationTest {

  private static final String EXCHANGE = "commands";
  private static final String ROUTING_KEY = "test.order.create";
  private static final long RETRY_TTL = 200;
  private static final long RECEIVE_TIMEOUT = 5000;

  private static RabbitMQContainer container;
  private static CachingConnectionFactory connectionFactory;
  private static RabbitTemplate rabbitTemplate;
  private static RabbitMqNamingStrategy naming;

  static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @BeforeAll
  static void startBroker() {
    container = new RabbitMQContainer("rabbitmq:3.13-management");
    container.start();
    connectionFactory = new CachingConnectionFactory(container.getHost(), container.getAmqpPort());
    connectionFactory.setUsername(container.getAdminUsername());
    connectionFactory.setPassword(container.getAdminPassword());
    rabbitTemplate = new RabbitTemplate(connectionFactory);
    naming = new DefaultRabbitMqNamingStrategy("it-retry");

    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    RabbitMqBusDeclarationBuilder builder = new RabbitMqBusDeclarationBuilder(naming);
    for (String app : List.of("app-a", "app-b")) {
      Declarables declarables =
          builder.buildWithRetryAndDeadLetter(app, EXCHANGE, List.of(ROUTING_KEY), RETRY_TTL);
      declarables.getDeclarablesByType(TopicExchange.class).forEach(admin::declareExchange);
      declarables.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
      declarables.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
    }
  }

  @AfterAll
  static void stopBroker() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
    if (container != null) {
      container.stop();
    }
  }

  @Test
  void failedCommandShouldBeRetriedAndDeadLetteredOnlyInTheFailingApplication() {
    CommandHandlerRegistry failingRegistry = mock(CommandHandlerRegistry.class);
    TestCommand command = new TestCommand("payload");
    when(failingRegistry.handle(command)).thenThrow(new IllegalStateException("boom"));
    RabbitMqCommandConsumer appA =
        new RabbitMqCommandConsumer(
            failingRegistry, List.of(), rabbitTemplate, naming, EXCHANGE, "app-a", null, 3);

    MessageProperties properties = new MessageProperties();
    properties.setHeader("cqrs.message.type", "command");
    properties.setHeader("custom", "kept");
    rabbitTemplate.send(
        naming.exchange(EXCHANGE),
        ROUTING_KEY,
        MessageBuilder.withBody("{\"data\":\"payload\"}".getBytes(StandardCharsets.UTF_8))
            .andProperties(properties)
            .build());

    // app-b receives its own copy once and handles it; it must never see app-a's retries.
    assertThat(rabbitTemplate.receive(naming.queue("app-b", EXCHANGE), RECEIVE_TIMEOUT))
        .isNotNull();

    // max-attempts = 3: the first delivery and two retries, all in app-a's main queue.
    for (int attempt = 1; attempt <= 3; attempt++) {
      Message delivery = rabbitTemplate.receive(naming.queue("app-a", EXCHANGE), RECEIVE_TIMEOUT);
      assertThat(delivery).as("delivery %d of app-a", attempt).isNotNull();
      assertThat((String) delivery.getMessageProperties().getHeader("custom")).isEqualTo("kept");
      assertThat(new String(delivery.getBody(), StandardCharsets.UTF_8))
          .isEqualTo("{\"data\":\"payload\"}");
      appA.consume(delivery, command);
    }

    Message deadLetter =
        rabbitTemplate.receive(naming.queueDeadLetter("app-a", EXCHANGE), RECEIVE_TIMEOUT);
    assertThat(deadLetter).isNotNull();
    assertThat((Integer) deadLetter.getMessageProperties().getHeader("cqrs.redelivery.count"))
        .isEqualTo(2);

    assertThat(rabbitTemplate.receive(naming.queue("app-a", EXCHANGE), RETRY_TTL * 3)).isNull();
    assertThat(rabbitTemplate.receive(naming.queue("app-b", EXCHANGE))).isNull();
    assertThat(rabbitTemplate.receive(naming.queueRetry("app-b", EXCHANGE))).isNull();
    assertThat(rabbitTemplate.receive(naming.queueDeadLetter("app-b", EXCHANGE))).isNull();
  }
}
