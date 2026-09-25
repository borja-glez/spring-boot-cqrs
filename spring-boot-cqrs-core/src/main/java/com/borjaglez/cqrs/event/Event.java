package com.borjaglez.cqrs.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

@Getter
public abstract class Event {

  // Not final so deserializers that only populate non-final fields (Jackson 3 by default) keep the
  // identity the message was sent with. There are no setters: the value never changes after
  // construction or deserialization.
  private String eventId;
  private Instant occurredOn;

  protected Event() {
    this.eventId = UUID.randomUUID().toString();
    this.occurredOn = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Event event)) return false;
    return Objects.equals(eventId, event.eventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId);
  }
}
