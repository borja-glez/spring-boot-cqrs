package com.borjaglez.cqrs.rabbitmq;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

/**
 * {@link QueryBus} that sends queries to the RabbitMQ queries exchange and waits for the reply.
 *
 * <p>Before a query is published, it passes the middlewares that declare {@link
 * DispatchPhase#OUTBOUND}, in the sending process; a middleware can stop the send by throwing.
 */
public class RabbitMqQueryBus implements QueryBus {

  private final RabbitMqPublisher publisher;
  private final RabbitMqNamingStrategy rabbitNaming;
  private final MessageNamingStrategy messageNaming;
  private final String exchangeName;
  private final List<BusMiddleware> middlewares;

  /** Creates a bus that runs no middleware before sending. */
  public RabbitMqQueryBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName) {
    this(publisher, rabbitNaming, messageNaming, exchangeName, List.of());
  }

  /**
   * Creates a bus that runs, before sending, the middlewares of {@code middlewares} that declare
   * {@link DispatchPhase#OUTBOUND}, in the order of the list.
   */
  public RabbitMqQueryBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName,
      List<BusMiddleware> middlewares) {
    this.publisher = publisher;
    this.rabbitNaming = rabbitNaming;
    this.messageNaming = messageNaming;
    this.exchangeName = exchangeName;
    this.middlewares = DispatchPhase.OUTBOUND.select(middlewares);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R ask(Query query) {
    return (R) send(query, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R ask(Query query, ParameterizedTypeReference<R> responseType) {
    return (R) send(query, responseType);
  }

  /** Runs the outbound middlewares with the request-reply send as the end of the chain. */
  private Object send(Query query, ParameterizedTypeReference<?> responseType) {
    try {
      return new DefaultMiddlewareChain(
              middlewares,
              message -> {
                String exchange = rabbitNaming.exchange(exchangeName);
                String routingKey = messageNaming.queryName(message.getClass());
                return responseType == null
                    ? publisher.publishAndReceive(exchange, routingKey, message, "query")
                    : publisher.publishAndReceive(
                        exchange, routingKey, message, "query", responseType);
              })
          .proceed(query);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new QueryHandlerExecutionException(e);
    }
  }
}
