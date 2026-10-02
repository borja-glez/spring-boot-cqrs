package com.borjaglez.cqrs.rabbitmq.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqEventConsumer;
import com.borjaglez.cqrs.rabbitmq.fixtures.IdempotentProjectors;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.infrastructure.DefaultRabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

/**
 * An event with two idempotent handlers in one application: the second fails once, RabbitMQ
 * redelivers the whole event through the retry queue, and only the failed handler runs again.
 */
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class RabbitMqIdempotencyIntegrationTest {

  private static final String EXCHANGE = "events";
  private static final String APP = "app";
  private static final String ROUTING_KEY = "test.order.created";
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
    naming = new DefaultRabbitMqNamingStrategy("it-idempotency");
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    Declarables declarables =
        new RabbitMqBusDeclarationBuilder(naming)
            .buildWithRetryAndDeadLetter(APP, EXCHANGE, List.of(ROUTING_KEY), RETRY_TTL);
    declarables.getDeclarablesByType(TopicExchange.class).forEach(admin::declareExchange);
    declarables.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
    declarables.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
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
  void redeliveredEventDoesNotReapplyTheHandlerThatSucceeded() throws Exception {
    IdempotentProjectors projectors = new IdempotentProjectors();
    EventHandlerRegistry registry = new EventHandlerRegistry();
    registry.register(
        TestEvent.class,
        projectors,
        IdempotentProjectors.class.getMethod("stock", TestEvent.class),
        ROUTING_KEY,
        null,
        null,
        true,
        "projectors#stock");
    registry.register(
        TestEvent.class,
        projectors,
        IdempotentProjectors.class.getMethod("email", TestEvent.class),
        ROUTING_KEY,
        null,
        null,
        true,
        "projectors#email");
    registry.setIdempotentInvoker(
        new IdempotentInvoker(
            new InMemoryIdempotencyStore(Duration.ofDays(7), Duration.ofMinutes(5))));
    RabbitMqEventConsumer consumer =
        new RabbitMqEventConsumer(
            registry, List.of(), rabbitTemplate, naming, EXCHANGE, APP, null, 3);
    TestEvent event = new TestEvent("payload");

    MessageProperties properties = new MessageProperties();
    properties.setHeader("cqrs.message.type", "event");
    rabbitTemplate.send(
        naming.exchange(EXCHANGE),
        ROUTING_KEY,
        MessageBuilder.withBody("{\"data\":\"payload\"}".getBytes(StandardCharsets.UTF_8))
            .andProperties(properties)
            .build());

    // First delivery: stock succeeds, email fails, the consumer re-sends to the retry queue.
    Message first = rabbitTemplate.receive(naming.queue(APP, EXCHANGE), RECEIVE_TIMEOUT);
    assertThat(first).isNotNull();
    consumer.consume(first, event);

    // Redelivery after the retry TTL, same eventId (the body is unchanged on the wire).
    Message second = rabbitTemplate.receive(naming.queue(APP, EXCHANGE), RECEIVE_TIMEOUT);
    assertThat(second).isNotNull();
    assertThat((Integer) second.getMessageProperties().getHeader("cqrs.redelivery.count"))
        .isEqualTo(1);
    consumer.consume(second, event);

    assertThat(projectors.stockCalls).hasValue(1);
    assertThat(projectors.emailCalls).hasValue(1);
    assertThat(rabbitTemplate.receive(naming.queue(APP, EXCHANGE), RETRY_TTL * 3)).isNull();
    assertThat(rabbitTemplate.receive(naming.queueDeadLetter(APP, EXCHANGE))).isNull();
  }
}
