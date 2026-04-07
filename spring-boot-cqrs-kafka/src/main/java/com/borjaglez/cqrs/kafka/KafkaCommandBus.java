package com.borjaglez.cqrs.kafka;

import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

public class KafkaCommandBus implements CommandBus {

  private final KafkaMessagePublisher publisher;
  private final KafkaRequestReplyClient requestReplyClient;
  private final KafkaTopicNamingStrategy topicNamingStrategy;
  private final String topicName;

  public KafkaCommandBus(
      KafkaMessagePublisher publisher,
      KafkaRequestReplyClient requestReplyClient,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName) {
    this.publisher = publisher;
    this.requestReplyClient = requestReplyClient;
    this.topicNamingStrategy = topicNamingStrategy;
    this.topicName = topicName;
  }

  @Override
  public void dispatch(Command command) {
    publisher.publish(topicNamingStrategy.topic(topicName), command);
  }

  @Override
  public void dispatchAndWait(Command command) {
    try {
      requestReplyClient.sendAndReceive(
          null, topicNamingStrategy.topic(topicName), command, null, KafkaRequestMode.WAIT);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  @Override
  public <R> R dispatchAndReceive(Command command) {
    try {
      return requestReplyClient.sendAndReceive(
          null, topicNamingStrategy.topic(topicName), command, null, KafkaRequestMode.REPLY);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  @Override
  public <R> R dispatchAndReceive(Command command, ParameterizedTypeReference<R> responseType) {
    try {
      return requestReplyClient.sendAndReceive(
          null,
          topicNamingStrategy.topic(topicName),
          command,
          responseType,
          KafkaRequestMode.REPLY);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }
}
