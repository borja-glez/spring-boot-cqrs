package com.borjaglez.cqrs.example.outbox.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor
public class OutboxEventEntity {

  @Id private UUID id;
  private String aggregateId;
  private String aggregateType;
  private String eventType;

  @Lob
  @Column(columnDefinition = "CLOB")
  private String payload;

  private Instant occurredAt;
  private Instant publishedAt;

  @Enumerated(EnumType.STRING)
  private OutboxStatus status;

  private int attempts;

  public OutboxEventEntity(
      UUID id,
      String aggregateId,
      String aggregateType,
      String eventType,
      String payload,
      Instant occurredAt) {
    this.id = id;
    this.aggregateId = aggregateId;
    this.aggregateType = aggregateType;
    this.eventType = eventType;
    this.payload = payload;
    this.occurredAt = occurredAt;
    this.status = OutboxStatus.PENDING;
    this.attempts = 0;
  }

  public void markPublished() {
    this.status = OutboxStatus.PUBLISHED;
    this.publishedAt = Instant.now();
  }

  public void markFailedAttempt() {
    this.attempts++;
  }
}
