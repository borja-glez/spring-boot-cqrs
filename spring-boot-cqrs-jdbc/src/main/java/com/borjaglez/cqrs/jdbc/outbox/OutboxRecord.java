package com.borjaglez.cqrs.jdbc.outbox;

/**
 * A pending outbox row, as the relay reads it. {@code context} is null when nothing was captured.
 */
public record OutboxRecord(
    long id,
    String eventId,
    String eventName,
    String eventClass,
    byte[] payload,
    byte[] context,
    int attempts) {}
