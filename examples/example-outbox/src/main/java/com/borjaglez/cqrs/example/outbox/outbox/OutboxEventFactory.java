package com.borjaglez.cqrs.example.outbox.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.borjaglez.cqrs.event.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OutboxEventFactory {

  private final ObjectMapper objectMapper;

  public OutboxEventFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public OutboxEventEntity create(String aggregateId, String aggregateType, Event event) {
    try {
      return new OutboxEventEntity(
          UUID.randomUUID(),
          aggregateId,
          aggregateType,
          event.getClass().getName(),
          objectMapper.writeValueAsString(event),
          Instant.now());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbox event", e);
    }
  }
}
