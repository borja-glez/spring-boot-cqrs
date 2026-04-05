package com.borjaglez.cqrs.example.multiservice.notification.domain;

import java.time.Instant;

import lombok.Getter;

@Getter
public class Notification {

  private final String id;
  private final String type;
  private final String message;
  private final Instant createdAt;

  public Notification(String id, String type, String message, Instant createdAt) {
    this.id = id;
    this.type = type;
    this.message = message;
    this.createdAt = createdAt;
  }
}
