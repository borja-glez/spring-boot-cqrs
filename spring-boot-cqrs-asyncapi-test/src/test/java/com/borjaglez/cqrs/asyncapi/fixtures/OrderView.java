package com.borjaglez.cqrs.asyncapi.fixtures;

/** Reply of {@link FindOrder}. */
public record OrderView(String orderId, String status, Money total) {}
