package com.borjaglez.cqrs.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;

class KafkaQueryBusTest {

  private KafkaRequestReplyClient requestReplyClient;
  private KafkaTopicNamingStrategy topicNamingStrategy;
  private KafkaQueryBus queryBus;

  @BeforeEach
  void setUp() {
    requestReplyClient = mock(KafkaRequestReplyClient.class);
    topicNamingStrategy = mock(KafkaTopicNamingStrategy.class);
    queryBus = new KafkaQueryBus(requestReplyClient, topicNamingStrategy, "queries");
    when(topicNamingStrategy.topic("queries")).thenReturn("cqrs.queries");
  }

  @Test
  void askShouldReturnReply() throws Exception {
    TestQuery query = new TestQuery("test");
    when(requestReplyClient.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY))
        .thenReturn("done");

    String result = queryBus.ask(query);

    assertThat(result).isEqualTo("done");
  }

  @Test
  void askShouldWrapCheckedExceptions() throws Exception {
    TestQuery query = new TestQuery("test");
    when(requestReplyClient.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY))
        .thenAnswer(
            invocation -> {
              throw new Exception("boom");
            });

    assertThatThrownBy(() -> queryBus.ask(query))
        .isInstanceOf(QueryHandlerExecutionException.class)
        .hasRootCauseMessage("boom");
  }

  @Test
  void askShouldRethrowRuntimeExceptions() throws Exception {
    TestQuery query = new TestQuery("test");
    when(requestReplyClient.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> queryBus.ask(query))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void askWithTypeShouldPassTypeReference() throws Exception {
    TestQuery query = new TestQuery("test");
    ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<String>() {};
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.queries", query, responseType, KafkaRequestMode.REPLY))
        .thenReturn("typed");

    String result = queryBus.ask(query, responseType);

    assertThat(result).isEqualTo("typed");
  }

  @Test
  void askWithTypeShouldWrapCheckedExceptions() throws Exception {
    TestQuery query = new TestQuery("test");
    ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<String>() {};
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.queries", query, responseType, KafkaRequestMode.REPLY))
        .thenAnswer(
            invocation -> {
              throw new Exception("boom");
            });

    assertThatThrownBy(() -> queryBus.ask(query, responseType))
        .isInstanceOf(QueryHandlerExecutionException.class)
        .hasRootCauseMessage("boom");
  }

  @Test
  void askWithTypeShouldRethrowRuntimeExceptions() throws Exception {
    TestQuery query = new TestQuery("test");
    ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<String>() {};
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.queries", query, responseType, KafkaRequestMode.REPLY))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> queryBus.ask(query, responseType))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }
}
