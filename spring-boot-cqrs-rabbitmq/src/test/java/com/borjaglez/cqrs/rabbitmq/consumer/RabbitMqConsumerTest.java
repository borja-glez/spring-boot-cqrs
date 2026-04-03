package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

class RabbitMqConsumerTest {

  private RabbitTemplate rabbitTemplate;
  private RabbitMqNamingStrategy namingStrategy;
  private TestableConsumer consumer;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    namingStrategy = mock(RabbitMqNamingStrategy.class);
    consumer = new TestableConsumer(rabbitTemplate, namingStrategy, 3);
  }

  @Test
  void handleConsumptionErrorShouldSendToRetryExchangeOnFirstFailure() {
    org.mockito.Mockito.when(namingStrategy.exchangeRetry("commands"))
        .thenReturn("cqrs.commands.retry");

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.retry", "#", message);
  }

  @Test
  void handleConsumptionErrorShouldIncrementRedeliveryCount() {
    org.mockito.Mockito.when(namingStrategy.exchangeRetry("commands"))
        .thenReturn("cqrs.commands.retry");

    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.redelivery.count", 1);
    Message message = MessageBuilder.withBody("{}".getBytes()).andProperties(props).build();

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.retry", "#", message);
    // After this call, the header should be 2
    org.assertj.core.api.Assertions.assertThat(
            (Integer) message.getMessageProperties().getHeader("cqrs.redelivery.count"))
        .isEqualTo(2);
  }

  @Test
  void handleConsumptionErrorShouldSendToDeadLetterWhenMaxRetriesExceeded() {
    org.mockito.Mockito.when(namingStrategy.exchangeDeadLetter("commands"))
        .thenReturn("cqrs.commands.dead_letter");

    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.redelivery.count", 3); // already at max
    Message message = MessageBuilder.withBody("{}".getBytes()).andProperties(props).build();

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.dead_letter", "#", message);
  }

  @Test
  void handleConsumptionErrorShouldTreatNonIntegerHeaderAsZero() {
    org.mockito.Mockito.when(namingStrategy.exchangeRetry("commands"))
        .thenReturn("cqrs.commands.retry");

    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.redelivery.count", "not-a-number");
    Message message = MessageBuilder.withBody("{}".getBytes()).andProperties(props).build();

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.retry", "#", message);
  }

  @Test
  void handleConsumptionErrorShouldSendToDeadLetterAtExactMaxRetries() {
    org.mockito.Mockito.when(namingStrategy.exchangeDeadLetter("events"))
        .thenReturn("cqrs.events.dead_letter");

    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.redelivery.count", 5);
    Message message = MessageBuilder.withBody("{}".getBytes()).andProperties(props).build();

    // Consumer with maxRetries=5, count=5 means we've exhausted retries
    TestableConsumer consumer5 = new TestableConsumer(rabbitTemplate, namingStrategy, 5);
    consumer5.handleConsumptionError(message, "events", "app");

    verify(rabbitTemplate).send("cqrs.events.dead_letter", "#", message);
  }

  /** Concrete subclass to test the abstract RabbitMqConsumer. */
  static class TestableConsumer extends RabbitMqConsumer {

    private final int maxRetries;

    TestableConsumer(
        RabbitTemplate rabbitTemplate, RabbitMqNamingStrategy namingStrategy, int maxRetries) {
      super(rabbitTemplate, namingStrategy);
      this.maxRetries = maxRetries;
    }

    @Override
    protected int getMaxRetries() {
      return maxRetries;
    }

    @Override
    public void handleConsumptionError(Message message, String exchangeName, String appName) {
      super.handleConsumptionError(message, exchangeName, appName);
    }
  }
}
