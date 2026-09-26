package com.borjaglez.cqrs.kafka;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;

/**
 * {@link CommandBus} that sends commands to the Kafka commands topic.
 *
 * <p>Before a command is sent, it passes the middlewares that declare {@link
 * DispatchPhase#OUTBOUND}, in the sending process. A middleware can stop the send by throwing: an
 * invalid command then fails with the exception of the validation middleware and nothing is sent.
 * The receiving service runs its {@link DispatchPhase#INBOUND} middlewares again.
 */
public class KafkaCommandBus implements CommandBus {

  private final KafkaMessagePublisher publisher;
  private final KafkaRequestReplyClient requestReplyClient;
  private final KafkaTopicNamingStrategy topicNamingStrategy;
  private final String topicName;
  private final List<BusMiddleware> middlewares;

  /** Creates a bus that runs no middleware before sending. */
  public KafkaCommandBus(
      KafkaMessagePublisher publisher,
      KafkaRequestReplyClient requestReplyClient,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName) {
    this(publisher, requestReplyClient, topicNamingStrategy, topicName, List.of());
  }

  /**
   * Creates a bus that runs, before sending, the middlewares of {@code middlewares} that declare
   * {@link DispatchPhase#OUTBOUND}, in the order of the list.
   */
  public KafkaCommandBus(
      KafkaMessagePublisher publisher,
      KafkaRequestReplyClient requestReplyClient,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName,
      List<BusMiddleware> middlewares) {
    this.publisher = publisher;
    this.requestReplyClient = requestReplyClient;
    this.topicNamingStrategy = topicNamingStrategy;
    this.topicName = topicName;
    this.middlewares = DispatchPhase.OUTBOUND.select(middlewares);
  }

  @Override
  public void dispatch(Command command) {
    send(
        command,
        (topic, message) -> {
          publisher.publish(topic, message);
          return null;
        });
  }

  @Override
  public void dispatchAndWait(Command command) {
    send(
        command,
        (topic, message) ->
            requestReplyClient.sendAndReceive(null, topic, message, null, KafkaRequestMode.WAIT));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatchAndReceive(Command command) {
    return (R)
        send(
            command,
            (topic, message) ->
                requestReplyClient.sendAndReceive(
                    null, topic, message, null, KafkaRequestMode.REPLY));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatchAndReceive(Command command, ParameterizedTypeReference<R> responseType) {
    return (R)
        send(
            command,
            (topic, message) ->
                requestReplyClient.sendAndReceive(
                    null, topic, message, responseType, KafkaRequestMode.REPLY));
  }

  /** Runs the outbound middlewares with {@code sender} as the end of the chain. */
  private Object send(Command command, Sender sender) {
    try {
      return new DefaultMiddlewareChain(
              middlewares, message -> sender.send(topicNamingStrategy.topic(topicName), message))
          .proceed(command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  @FunctionalInterface
  private interface Sender {
    Object send(String topic, Object message) throws Exception;
  }
}
