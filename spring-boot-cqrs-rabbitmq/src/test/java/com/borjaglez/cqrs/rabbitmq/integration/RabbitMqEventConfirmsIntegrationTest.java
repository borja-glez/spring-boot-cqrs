package com.borjaglez.cqrs.rabbitmq.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;

import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.PublishNotConfirmedException;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;
import com.borjaglez.cqrs.rabbitmq.fixtures.PlainTextMessageConverter;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.infrastructure.DefaultRabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

/**
 * Publisher confirms on a real broker: an event the broker accepts is confirmed, and one it rejects
 * makes {@link RabbitMqEventBus#publish} fail.
 */
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class RabbitMqEventConfirmsIntegrationTest {

  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

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
    connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(new PlainTextMessageConverter());
    naming = new DefaultRabbitMqNamingStrategy("it-confirms");

    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    TopicExchange accepting = new TopicExchange(naming.exchange("accepting"));
    Queue acceptingQueue = new Queue("it-confirms.accepting.queue");
    admin.declareExchange(accepting);
    admin.declareQueue(acceptingQueue);
    admin.declareBinding(BindingBuilder.bind(acceptingQueue).to(accepting).with("#"));

    // A queue that is always full and rejects new messages: the broker nacks every publish.
    TopicExchange full = new TopicExchange(naming.exchange("full"));
    Queue fullQueue =
        new Queue(
            "it-confirms.full.queue",
            true,
            false,
            false,
            Map.of("x-max-length", 0, "x-overflow", "reject-publish"));
    admin.declareExchange(full);
    admin.declareQueue(fullQueue);
    admin.declareBinding(BindingBuilder.bind(fullQueue).to(full).with("#"));
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
  void publishShouldReturnOnceTheBrokerConfirmsTheEvent() {
    RabbitMqEventBus eventBus = eventBus("accepting");

    eventBus.publish(new TestEvent("accepted"));

    assertThat(rabbitTemplate.receive("it-confirms.accepting.queue", 5000)).isNotNull();
  }

  @Test
  void publishShouldFailWhenTheBrokerRejectsTheEvent() {
    RabbitMqEventBus eventBus = eventBus("full");

    assertThatThrownBy(() -> eventBus.publish(new TestEvent("rejected")))
        .isInstanceOf(PublishNotConfirmedException.class)
        .hasMessageContaining("was rejected by the broker");
  }

  private static RabbitMqEventBus eventBus(String exchange) {
    return new RabbitMqEventBus(
        new RabbitMqPublisher(rabbitTemplate),
        naming,
        new DefaultMessageNamingStrategy("cqrs"),
        exchange,
        CONFIRM_TIMEOUT);
  }
}
