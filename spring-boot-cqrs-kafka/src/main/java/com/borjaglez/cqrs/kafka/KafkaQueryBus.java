package com.borjaglez.cqrs.kafka;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;

/**
 * {@link QueryBus} that sends queries to the Kafka queries topic and waits for the reply.
 *
 * <p>Before a query is sent, it passes the middlewares that declare {@link DispatchPhase#OUTBOUND},
 * in the sending process; a middleware can stop the send by throwing.
 */
public class KafkaQueryBus implements QueryBus {

  private final KafkaRequestReplyClient requestReplyClient;
  private final KafkaTopicNamingStrategy topicNamingStrategy;
  private final String topicName;
  private final List<BusMiddleware> middlewares;

  /** Creates a bus that runs no middleware before sending. */
  public KafkaQueryBus(
      KafkaRequestReplyClient requestReplyClient,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName) {
    this(requestReplyClient, topicNamingStrategy, topicName, List.of());
  }

  /**
   * Creates a bus that runs, before sending, the middlewares of {@code middlewares} that declare
   * {@link DispatchPhase#OUTBOUND}, in the order of the list.
   */
  public KafkaQueryBus(
      KafkaRequestReplyClient requestReplyClient,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName,
      List<BusMiddleware> middlewares) {
    this.requestReplyClient = requestReplyClient;
    this.topicNamingStrategy = topicNamingStrategy;
    this.topicName = topicName;
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
              message ->
                  requestReplyClient.sendAndReceive(
                      null,
                      topicNamingStrategy.topic(topicName),
                      message,
                      responseType,
                      KafkaRequestMode.REPLY))
          .proceed(query);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new QueryHandlerExecutionException(e);
    }
  }
}
