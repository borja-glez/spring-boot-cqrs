package com.borjaglez.cqrs.aot.scanned;

/** The reply to {@link ChargeCard}: a plain record, not a message, that the caller deserializes. */
public record CardCharged(boolean charged, String reason) {}
