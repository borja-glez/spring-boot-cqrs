package com.borjaglez.cqrs.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.fixtures.RecordingMiddleware;
import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
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
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.queries", query, null, KafkaRequestMode.REPLY))
        .thenReturn("done");

    String result = queryBus.ask(query);

    assertThat(result).isEqualTo("done");
  }

  @Test
  void askShouldWrapCheckedExceptions() throws Exception {
    TestQuery query = new TestQuery("test");
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.queries", query, null, KafkaRequestMode.REPLY))
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
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.queries", query, null, KafkaRequestMode.REPLY))
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

  @Test
  void askRunsOutboundMiddlewaresBeforeSendingAndSkipsTheOthers() throws Exception {
    List<String> calls = new ArrayList<>();
    List<String> correlationIds = new ArrayList<>();
    TestQuery query = new TestQuery("test");
    when(requestReplyClient.sendAndReceive(eq(null), eq("cqrs.queries"), eq(query), any(), any()))
        .thenAnswer(
            invocation -> {
              calls.add("send");
              correlationIds.add(
                  MessageContext.current().get(MessageContext.CORRELATION_ID_KEY).orElseThrow());
              return "answer";
            });
    KafkaQueryBus bus =
        new KafkaQueryBus(
            requestReplyClient,
            topicNamingStrategy,
            "queries",
            List.of(
                new ContextPropagationMiddleware(true, List.of(), () -> "sender-id"),
                new RecordingMiddleware(calls, "outbound", DispatchPhase.OUTBOUND),
                new RecordingMiddleware(calls, "inbound", DispatchPhase.INBOUND)));

    String answer = bus.ask(query);
    String typed = bus.ask(query, new ParameterizedTypeReference<String>() {});

    assertThat(answer).isEqualTo("answer");
    assertThat(typed).isEqualTo("answer");
    assertThat(calls).containsExactly("outbound", "send", "outbound", "send");
    assertThat(correlationIds).containsExactly("sender-id", "sender-id");
  }

  @Test
  void outboundMiddlewareCanShortCircuitTheSend() {
    Exception failure = new Exception("denied");
    KafkaQueryBus bus =
        new KafkaQueryBus(
            requestReplyClient, topicNamingStrategy, "queries", List.of(new ShortCircuit(failure)));

    assertThatThrownBy(() -> bus.ask(new TestQuery("test")))
        .isInstanceOf(QueryHandlerExecutionException.class)
        .hasCause(failure);
    verifyNoInteractions(requestReplyClient);
  }

  /** Outbound middleware that fails with a checked exception instead of calling the chain. */
  private static final class ShortCircuit implements BusMiddleware {

    private final Exception failure;

    ShortCircuit(Exception failure) {
      this.failure = failure;
    }

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      throw failure;
    }

    @Override
    public Set<DispatchPhase> phases() {
      return Set.of(DispatchPhase.OUTBOUND);
    }
  }
}
