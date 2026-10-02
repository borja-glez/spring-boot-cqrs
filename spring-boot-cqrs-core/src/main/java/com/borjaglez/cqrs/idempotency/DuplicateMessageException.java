package com.borjaglez.cqrs.idempotency;

import lombok.Getter;

/**
 * Thrown to the caller of {@code dispatchAndReceive} when an {@link Idempotent} command handler
 * that returns a result already processed the command: the result is not stored, so it cannot be
 * returned again.
 */
@Getter
public class DuplicateMessageException extends RuntimeException {

  private final String handlerId;
  private final String messageId;

  public DuplicateMessageException(String handlerId, String messageId) {
    super("Message " + messageId + " was already processed by idempotent handler " + handlerId);
    this.handlerId = handlerId;
    this.messageId = messageId;
  }
}
