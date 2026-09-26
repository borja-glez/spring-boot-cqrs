package com.borjaglez.cqrs;

/**
 * Opt-in contract for a command or an event that belongs to an entity (an aggregate, say) and must
 * keep its order relative to the other messages of that entity.
 *
 * <p>Transports that support ordering by key use {@link #messageKey()}: the Kafka adapter sends it
 * as the record key, so every message with the same key, whatever its type, lands on the same
 * partition of a topic and is consumed in publication order. It is also sent as the {@code
 * cqrs.message.key} header. A {@code null} or blank key means "no key" and the transport falls back
 * to its configured behaviour. Queries have no ordering need; a query that implements this
 * interface is published as if it did not.
 *
 * <pre>{@code
 * public class OrderCancelled extends Event implements KeyedMessage {
 *   private UUID orderId;
 *
 *   @Override
 *   public String messageKey() {
 *     return orderId.toString();
 *   }
 * }
 * }</pre>
 *
 * <p>The method name is deliberately not a bean getter, so JSON serializers do not add it to the
 * payload, and no reflection is involved, so it needs no GraalVM hints.
 */
public interface KeyedMessage {

  /**
   * Returns the key that groups this message with the other messages of the same entity, or {@code
   * null} (or a blank string) when it has none.
   */
  String messageKey();
}
