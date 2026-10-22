package com.borjaglez.cqrs.asyncapi;

/** What the service that owns an AsyncAPI document does with a message. */
public enum MessageRole {
  /** Sent by a {@code send} operation. */
  SENT,
  /** Received by a {@code receive} operation. */
  RECEIVED,
  /** Sent as the reply of a {@code receive} operation. */
  REPLY_SENT,
  /** Received as the reply of a {@code send} operation. */
  REPLY_RECEIVED
}
