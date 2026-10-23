package com.borjaglez.cqrs.jdbc.outbox;

import java.time.Instant;

/**
 * A snapshot of the outbox: rows waiting to be relayed, rows set aside after unreadable attempts,
 * and when the oldest pending row was written ({@code null} when nothing is pending).
 */
public record OutboxStats(long pending, long setAside, Instant oldestPendingCreatedAt) {}
