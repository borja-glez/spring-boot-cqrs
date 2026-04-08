package com.borjaglez.cqrs.example.outbox.outbox;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OutboxPublisher {

  private static final String ALLOWED_EVENT_PACKAGE =
      "com.borjaglez.cqrs.example.outbox.event.";
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxEventRepository outboxEventRepository;
  private final EventBus eventBus;
  private final ObjectMapper objectMapper;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      EventBus eventBus,
      ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.eventBus = eventBus;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${example.outbox.publish-delay:1000}")
  @Transactional
  public void publishPending() {
    List<OutboxEventEntity> events =
        outboxEventRepository.findTop20ByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING);

    for (OutboxEventEntity outboxEvent : events) {
      try {
        Event event = deserialize(outboxEvent);
        eventBus.publish(event);
        outboxEvent.markPublished();
      } catch (Exception e) {
        outboxEvent.markFailedAttempt();
        log.warn("Failed to publish outbox event {}", outboxEvent.getId(), e);
      }
    }
  }

  private Event deserialize(OutboxEventEntity outboxEvent) {
    try {
      String eventType = outboxEvent.getEventType();
      if (!eventType.startsWith(ALLOWED_EVENT_PACKAGE)) {
        throw new IllegalStateException("Outbox event type is not allowed: " + eventType);
      }
      Class<?> eventClass = Class.forName(eventType);
      if (!Event.class.isAssignableFrom(eventClass)) {
        throw new IllegalStateException("Outbox event type must extend Event: " + eventType);
      }
      return (Event) objectMapper.readValue(outboxEvent.getPayload(), eventClass);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to deserialize outbox event " + outboxEvent.getId(), e);
    }
  }
}
