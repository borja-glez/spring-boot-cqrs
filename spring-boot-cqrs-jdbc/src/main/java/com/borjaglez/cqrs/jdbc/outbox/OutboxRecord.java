package com.borjaglez.cqrs.jdbc.outbox;

/**
 * A pending outbox row, as the relay reads it. {@code context} is null when nothing was captured.
 * {@code attempts} counts every failed attempt; {@code readFailures} only those that could not read
 * the row, which decide when it is set aside.
 */
public record OutboxRecord(
    long id,
    String eventId,
    String eventName,
    String eventClass,
    byte[] payload,
    byte[] context,
    int attempts,
    int readFailures) {}
