package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.query.QueryNotRegisteredException;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQuery;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

class RabbitMqQueryConsumerTest {

  private QueryHandlerRegistry registry;
  private RabbitMqQueryConsumer consumer;

  @BeforeEach
  void setUp() {
    registry = mock(QueryHandlerRegistry.class);
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    RabbitMqNamingStrategy namingStrategy = mock(RabbitMqNamingStrategy.class);
    consumer = new RabbitMqQueryConsumer(registry, rabbitTemplate, namingStrategy);
  }

  @Test
  void consumeShouldReturnRegistryResult() {
    TestQuery query = new TestQuery("test-data");
    when(registry.handle(query)).thenReturn("query-result");

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    Object result = consumer.consume(message, query);

    assertThat(result).isEqualTo("query-result");
  }

  @Test
  void consumeShouldPropagateException() {
    TestQuery query = new TestQuery("test-data");
    when(registry.handle(query)).thenThrow(new QueryNotRegisteredException(TestQuery.class));

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    assertThatThrownBy(() -> consumer.consume(message, query))
        .isInstanceOf(QueryNotRegisteredException.class);
  }
}
