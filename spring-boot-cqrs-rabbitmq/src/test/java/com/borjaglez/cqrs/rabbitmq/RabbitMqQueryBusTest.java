package com.borjaglez.cqrs.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQuery;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

class RabbitMqQueryBusTest {

  private RabbitMqPublisher publisher;
  private RabbitMqNamingStrategy rabbitNaming;
  private MessageNamingStrategy messageNaming;
  private RabbitMqQueryBus queryBus;

  @BeforeEach
  void setUp() {
    publisher = mock(RabbitMqPublisher.class);
    rabbitNaming = mock(RabbitMqNamingStrategy.class);
    messageNaming = mock(MessageNamingStrategy.class);
    queryBus = new RabbitMqQueryBus(publisher, rabbitNaming, messageNaming, "queries");
  }

  @Test
  void askShouldSendAndReceive() {
    TestQuery query = new TestQuery("test-data");
    when(rabbitNaming.exchange("queries")).thenReturn("cqrs.queries");
    when(messageNaming.queryName(TestQuery.class)).thenReturn("test.order.get");
    when(publisher.publishAndReceive("cqrs.queries", "test.order.get", query, "query"))
        .thenReturn("query-result");

    String result = queryBus.ask(query);

    assertThat(result).isEqualTo("query-result");
  }

  @Test
  void askShouldRethrowRuntimeException() {
    TestQuery query = new TestQuery("test-data");
    when(rabbitNaming.exchange("queries")).thenReturn("cqrs.queries");
    when(messageNaming.queryName(TestQuery.class)).thenReturn("test.order.get");
    when(publisher.publishAndReceive("cqrs.queries", "test.order.get", query, "query"))
        .thenThrow(new RuntimeException("remote error"));

    assertThatThrownBy(() -> queryBus.ask(query))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("remote error");
  }

  @Test
  void askShouldReturnNullWhenPublisherReturnsNull() {
    TestQuery query = new TestQuery("test-data");
    when(rabbitNaming.exchange("queries")).thenReturn("cqrs.queries");
    when(messageNaming.queryName(TestQuery.class)).thenReturn("test.order.get");
    when(publisher.publishAndReceive("cqrs.queries", "test.order.get", query, "query"))
        .thenReturn(null);

    String result = queryBus.ask(query);

    assertThat(result).isNull();
  }

  @Test
  void askShouldWrapCheckedExceptionInQueryHandlerExecutionException() {
    TestQuery query = new TestQuery("test-data");
    when(rabbitNaming.exchange("queries")).thenReturn("cqrs.queries");
    when(messageNaming.queryName(TestQuery.class)).thenReturn("test.order.get");
    Exception checkedException = new Exception("checked error");
    when(publisher.publishAndReceive("cqrs.queries", "test.order.get", query, "query"))
        .thenAnswer(
            invocation -> {
              throw checkedException;
            });

    assertThatThrownBy(() -> queryBus.ask(query))
        .isInstanceOf(QueryHandlerExecutionException.class)
        .hasCause(checkedException);
  }
}
