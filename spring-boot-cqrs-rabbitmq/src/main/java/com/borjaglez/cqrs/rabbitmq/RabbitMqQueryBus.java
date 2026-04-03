package com.borjaglez.cqrs.rabbitmq;

import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

public class RabbitMqQueryBus implements QueryBus {

  private final RabbitMqPublisher publisher;
  private final RabbitMqNamingStrategy rabbitNaming;
  private final MessageNamingStrategy messageNaming;
  private final String exchangeName;

  public RabbitMqQueryBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName) {
    this.publisher = publisher;
    this.rabbitNaming = rabbitNaming;
    this.messageNaming = messageNaming;
    this.exchangeName = exchangeName;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R ask(Query query) {
    String exchange = rabbitNaming.exchange(exchangeName);
    String routingKey = messageNaming.queryName(query.getClass());
    try {
      return (R) publisher.publishAndReceive(exchange, routingKey, query, "query");
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new QueryHandlerExecutionException(e);
    }
  }
}
