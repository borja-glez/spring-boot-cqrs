package com.borjaglez.cqrs.kafka;

import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;

public class KafkaQueryBus implements QueryBus {

  private final KafkaRequestReplyClient requestReplyClient;
  private final KafkaTopicNamingStrategy topicNamingStrategy;
  private final String topicName;

  public KafkaQueryBus(
      KafkaRequestReplyClient requestReplyClient,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName) {
    this.requestReplyClient = requestReplyClient;
    this.topicNamingStrategy = topicNamingStrategy;
    this.topicName = topicName;
  }

  @Override
  public <R> R ask(Query query) {
    try {
      return requestReplyClient.sendAndReceive(
          null, topicNamingStrategy.topic(topicName), query, null, KafkaRequestMode.REPLY);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new QueryHandlerExecutionException(e);
    }
  }

  @Override
  public <R> R ask(Query query, ParameterizedTypeReference<R> responseType) {
    try {
      return requestReplyClient.sendAndReceive(
          null, topicNamingStrategy.topic(topicName), query, responseType, KafkaRequestMode.REPLY);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new QueryHandlerExecutionException(e);
    }
  }
}
