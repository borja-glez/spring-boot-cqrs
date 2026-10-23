package com.borjaglez.cqrs.asyncapi;

/**
 * A message of an {@link AsyncApiContract}.
 *
 * @param name the {@code name} of the AsyncAPI Message Object: the logical name of the message (for
 *     example {@code shop.orders.1.event.order.order-placed}) or, for a reply, the name the
 *     contract gives it
 * @param role what the service does with the message
 * @param channel the key of the message's channel in the {@code channels} of the document
 */
public record ContractMessage(String name, MessageRole role, String channel) {}
