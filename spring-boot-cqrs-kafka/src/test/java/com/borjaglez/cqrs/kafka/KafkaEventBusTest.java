package com.borjaglez.cqrs.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.kafka.fixtures.RecordingMiddleware;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

class KafkaEventBusTest {

  private KafkaMessagePublisher publisher;
  private KafkaTopicNamingStrategy topicNamingStrategy;
  private KafkaEventBus eventBus;

  @BeforeEach
  void setUp() {
    publisher = mock(KafkaMessagePublisher.class);
    topicNamingStrategy = mock(KafkaTopicNamingStrategy.class);
    eventBus = new KafkaEventBus(publisher, topicNamingStrategy, "events");
    when(topicNamingStrategy.topic("events")).thenReturn("cqrs.events");
  }

  @Test
  void publishShouldUseConfiguredTopic() {
    TestEvent event = new TestEvent("value");

    eventBus.publish(event);

    verify(publisher).publish("cqrs.events", event);
  }

  @Test
  void publishShouldPropagateTheBrokerFailureUnchanged() {
    TestEvent event = new TestEvent("value");
    IllegalStateException failure = new IllegalStateException("broker down");
    doThrow(failure).when(publisher).publish("cqrs.events", event);

    assertThatThrownBy(() -> eventBus.publish(event))
        .isSameAs(failure)
        .hasMessageContaining("broker down");
  }

  @Test
  void publishListShouldPublishEachEventInOrder() {
    TestEvent first = new TestEvent("one");
    TestEvent second = new TestEvent("two");

    eventBus.publish(List.of(first, second));

    var order = inOrder(publisher);
    order.verify(publisher).publish("cqrs.events", first);
    order.verify(publisher).publish("cqrs.events", second);
  }

  @Test
  void publishListShouldStopAtTheFirstFailureAndPropagateIt() {
    TestEvent first = new TestEvent("one");
    TestEvent failing = new TestEvent("two");
    TestEvent notSent = new TestEvent("three");
    IllegalStateException failure = new IllegalStateException("broker down");
    doThrow(failure).when(publisher).publish("cqrs.events", failing);

    assertThatThrownBy(() -> eventBus.publish(List.of(first, failing, notSent))).isSameAs(failure);

    verify(publisher).publish("cqrs.events", first);
    verify(publisher).publish("cqrs.events", failing);
    verify(publisher, never()).publish("cqrs.events", notSent);
  }

  @Test
  void publishRunsOutboundMiddlewaresBeforePublishingAndSkipsTheOthers() {
    List<String> calls = new ArrayList<>();
    List<String> correlationIds = new ArrayList<>();
    TestEvent event = new TestEvent("value");
    doAnswer(
            invocation -> {
              calls.add("publish");
              correlationIds.add(
                  MessageContext.current().get(MessageContext.CORRELATION_ID_KEY).orElseThrow());
              return null;
            })
        .when(publisher)
        .publish("cqrs.events", event);
    KafkaEventBus bus =
        new KafkaEventBus(
            publisher,
            topicNamingStrategy,
            "events",
            List.of(
                new ContextPropagationMiddleware(true, List.of(), () -> "sender-id"),
                new RecordingMiddleware(calls, "outbound", DispatchPhase.OUTBOUND),
                new RecordingMiddleware(calls, "local", DispatchPhase.LOCAL)));

    bus.publish(event);

    assertThat(calls).containsExactly("outbound", "publish");
    assertThat(correlationIds).containsExactly("sender-id");
  }

  @Test
  void outboundMiddlewareCanShortCircuitThePublish() {
    Exception failure = new Exception("denied");
    KafkaEventBus bus =
        new KafkaEventBus(
            publisher, topicNamingStrategy, "events", List.of(new ShortCircuit(failure)));

    assertThatThrownBy(() -> bus.publish(new TestEvent("value")))
        .isInstanceOf(EventHandlerExecutionException.class)
        .hasCause(failure);
    verifyNoInteractions(publisher);
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
