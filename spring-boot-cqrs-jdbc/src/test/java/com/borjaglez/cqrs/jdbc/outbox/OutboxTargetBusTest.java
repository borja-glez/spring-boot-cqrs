package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.fixtures.TestRecordingEventBus;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

class OutboxTargetBusTest {

  private final EventBus local = new TestRecordingEventBus();
  private final EventBus kafka = new TestRecordingEventBus();
  private final EventBus outbox = mock(OutboxEventBus.class);

  private Map<String, EventBus> buses(Object... namesAndBuses) {
    Map<String, EventBus> buses = new LinkedHashMap<>();
    for (int i = 0; i < namesAndBuses.length; i += 2) {
      buses.put((String) namesAndBuses[i], (EventBus) namesAndBuses[i + 1]);
    }
    return buses;
  }

  @Test
  void picksTheOnlyRemoteBus() {
    assertThat(
            OutboxTargetBus.resolve(
                buses("springEventBus", local, "kafkaEventBus", kafka, "outboxEventBus", outbox),
                null))
        .isSameAs(kafka);
  }

  @Test
  void configuredNameWins() {
    assertThat(
            OutboxTargetBus.resolve(
                buses("springEventBus", local, "kafkaEventBus", kafka), "springEventBus"))
        .isSameAs(local);
  }

  @Test
  void blankConfiguredNameIsIgnored() {
    assertThat(OutboxTargetBus.resolve(buses("kafkaEventBus", kafka), " ")).isSameAs(kafka);
  }

  @Test
  void unknownConfiguredNameFails() {
    assertThatThrownBy(() -> OutboxTargetBus.resolve(buses("kafkaEventBus", kafka), "nope"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope")
        .hasMessageContaining("kafkaEventBus");
  }

  @Test
  void configuredNameCannotBeTheOutboxItself() {
    assertThatThrownBy(
            () -> OutboxTargetBus.resolve(buses("outboxEventBus", outbox), "outboxEventBus"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outbox bus itself");
  }

  @Test
  void noCandidateFails() {
    assertThatThrownBy(
            () ->
                OutboxTargetBus.resolve(
                    buses("springEventBus", local, "outboxEventBus", outbox), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no event bus")
        .hasMessageContaining("cqrs.outbox.relay.event-bus");
  }

  @Test
  void severalCandidatesFail() {
    assertThatThrownBy(
            () -> OutboxTargetBus.resolve(buses("kafkaEventBus", kafka, "otherBus", local), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("[kafkaEventBus, otherBus]")
        .hasMessageContaining("cqrs.outbox.relay.event-bus");
  }

  @Test
  void rabbitMqWithoutConfirmsIsFlagged() {
    EventBus rabbit =
        new RabbitMqEventBus(
            mock(RabbitMqPublisher.class),
            mock(RabbitMqNamingStrategy.class),
            mock(MessageNamingStrategy.class),
            "events");

    assertThat(OutboxTargetBus.publishesWithoutConfirms(rabbit, new MockEnvironment())).isTrue();
    assertThat(
            OutboxTargetBus.publishesWithoutConfirms(
                rabbit,
                new MockEnvironment()
                    .withProperty("cqrs.rabbitmq.events.confirms.enabled", "true")))
        .isFalse();
    assertThat(OutboxTargetBus.publishesWithoutConfirms(kafka, new MockEnvironment())).isFalse();
  }
}
