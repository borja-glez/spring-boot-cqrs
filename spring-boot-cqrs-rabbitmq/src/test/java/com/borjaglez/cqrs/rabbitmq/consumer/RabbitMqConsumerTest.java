package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

class RabbitMqConsumerTest {

  private static final String REDELIVERY_COUNT = "cqrs.redelivery.count";

  private RabbitTemplate rabbitTemplate;
  private RabbitMqNamingStrategy namingStrategy;
  private TestableConsumer consumer;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    namingStrategy = mock(RabbitMqNamingStrategy.class);
    when(namingStrategy.exchangeRetry("commands")).thenReturn("cqrs.commands.retry");
    when(namingStrategy.exchangeDeadLetter("commands")).thenReturn("cqrs.commands.dead_letter");
    consumer = new TestableConsumer(rabbitTemplate, namingStrategy);
  }

  private static Message message(Object redeliveryCount) {
    MessageProperties props = new MessageProperties();
    props.setHeader("custom", "kept");
    if (redeliveryCount != null) {
      props.setHeader(REDELIVERY_COUNT, redeliveryCount);
    }
    return MessageBuilder.withBody("{\"a\":1}".getBytes()).andProperties(props).build();
  }

  @Test
  void handleConsumptionErrorShouldSendToRetryExchangeOnFirstFailure() {
    Message message = message(null);

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.retry", "app", message);
  }

  @Test
  void handleConsumptionErrorShouldRouteRetryWithTheFailingApplicationName() {
    Message message = message(null);

    consumer.handleConsumptionError(message, "commands", "order-service");

    verify(rabbitTemplate).send("cqrs.commands.retry", "order-service", message);
    verify(rabbitTemplate, never()).send(anyString(), eq("#"), any(Message.class));
  }

  @Test
  void handleConsumptionErrorShouldRouteDeadLetterWithTheFailingApplicationName() {
    Message message = message(5);

    consumer.handleConsumptionError(message, "commands", "order-service");

    verify(rabbitTemplate).send("cqrs.commands.dead_letter", "order-service", message);
    verify(rabbitTemplate, never()).send(anyString(), eq("#"), any(Message.class));
  }

  @Test
  void handleConsumptionErrorShouldIncrementRedeliveryCountAndKeepTheRestOfTheMessage() {
    Message message = message(1);

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.retry", "app", message);
    assertThat((Integer) message.getMessageProperties().getHeader(REDELIVERY_COUNT)).isEqualTo(2);
    assertThat((String) message.getMessageProperties().getHeader("custom")).isEqualTo("kept");
    assertThat(message.getBody()).isEqualTo("{\"a\":1}".getBytes());
  }

  @Test
  void handleConsumptionErrorShouldTreatNonIntegerHeaderAsZero() {
    Message message = message("not-a-number");

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.retry", "app", message);
  }

  @Test
  void defaultMaxAttemptsShouldBeThreeDeliveriesInTotal() {
    assertThat(consumer.getMaxAttempts()).isEqualTo(RabbitMqConsumer.DEFAULT_MAX_ATTEMPTS);
    assertThat(consumer.getMaxAttempts()).isEqualTo(3);
    assertThat(consumer.getMaxRetries()).isEqualTo(2);

    // The third delivery (after two retries) fails: it must go to the dead-letter exchange.
    Message message = message(2);

    consumer.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.dead_letter", "app", message);
    verify(rabbitTemplate, never()).send(eq("cqrs.commands.retry"), anyString(), any());
  }

  @Test
  void maxAttemptsOfThreeShouldRetryTwiceThenDeadLetter() {
    TestableConsumer threeAttempts = new TestableConsumer(rabbitTemplate, namingStrategy, 3);
    Message message = message(null);

    threeAttempts.handleConsumptionError(message, "commands", "app");
    threeAttempts.handleConsumptionError(message, "commands", "app");
    threeAttempts.handleConsumptionError(message, "commands", "app");

    InOrder inOrder = inOrder(rabbitTemplate);
    inOrder.verify(rabbitTemplate, times(2)).send("cqrs.commands.retry", "app", message);
    inOrder.verify(rabbitTemplate).send("cqrs.commands.dead_letter", "app", message);
  }

  @Test
  void maxAttemptsOfOneShouldSendStraightToDeadLetter() {
    TestableConsumer singleAttempt = new TestableConsumer(rabbitTemplate, namingStrategy, 1);
    Message message = message(null);

    singleAttempt.handleConsumptionError(message, "commands", "app");

    verify(rabbitTemplate).send("cqrs.commands.dead_letter", "app", message);
    verify(rabbitTemplate, never()).send(eq("cqrs.commands.retry"), anyString(), any());
  }

  @Test
  void maxAttemptsBelowOneShouldBeRejected() {
    assertThatThrownBy(() -> new TestableConsumer(rabbitTemplate, namingStrategy, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max-attempts");
  }

  /** Concrete subclass to test the abstract RabbitMqConsumer. */
  static class TestableConsumer extends RabbitMqConsumer {

    TestableConsumer(RabbitTemplate rabbitTemplate, RabbitMqNamingStrategy namingStrategy) {
      super(rabbitTemplate, namingStrategy);
    }

    TestableConsumer(
        RabbitTemplate rabbitTemplate, RabbitMqNamingStrategy namingStrategy, int maxAttempts) {
      super(rabbitTemplate, namingStrategy, maxAttempts);
    }

    @Override
    public void handleConsumptionError(Message message, String exchangeName, String appName) {
      super.handleConsumptionError(message, exchangeName, appName);
    }

    @Override
    public int getMaxAttempts() {
      return super.getMaxAttempts();
    }

    @Override
    public int getMaxRetries() {
      return super.getMaxRetries();
    }
  }
}
