package com.borjaglez.cqrs.jdbc.outbox;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import com.borjaglez.cqrs.event.EventBus;

/** Chooses the event bus the outbox relay publishes through. */
final class OutboxTargetBus {

  static final String PROPERTY = "cqrs.outbox.relay.event-bus";
  static final String SPRING_EVENT_BUS = "springEventBus";
  static final String RABBIT_EVENT_BUS_CLASS = "com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus";
  static final String RABBIT_CONFIRMS_PROPERTY = "cqrs.rabbitmq.events.confirms.enabled";

  private OutboxTargetBus() {}

  /** The bean name of the event bus the relay publishes through. */
  static String resolve(Map<String, EventBus> buses, String configuredName) {
    if (StringUtils.hasText(configuredName)) {
      EventBus bus = buses.get(configuredName);
      if (bus == null) {
        throw new IllegalStateException(
            PROPERTY
                + " names '"
                + configuredName
                + "', but there is no EventBus bean with that name; event buses: "
                + new TreeMap<>(buses).keySet());
      }
      if (bus instanceof OutboxEventBus) {
        throw new IllegalStateException(
            PROPERTY + " cannot name the outbox bus itself ('" + configuredName + "')");
      }
      return configuredName;
    }
    Map<String, EventBus> candidates = new TreeMap<>();
    buses.forEach(
        (name, bus) -> {
          if (!SPRING_EVENT_BUS.equals(name) && !(bus instanceof OutboxEventBus)) {
            candidates.put(name, bus);
          }
        });
    if (candidates.size() == 1) {
      return candidates.keySet().iterator().next();
    }
    String problem =
        candidates.isEmpty()
            ? "The outbox relay found no event bus to publish through (such as kafkaEventBus or"
                + " rabbitMqEventBus)"
            : "The outbox relay found several event buses to publish through: "
                + candidates.keySet();
    throw new IllegalStateException(
        problem + "; set " + PROPERTY + " to the bean name of the one to use");
  }

  /** Whether {@code bus} is the RabbitMQ event bus without publisher confirms. */
  static boolean publishesWithoutConfirms(EventBus bus, Environment environment) {
    return RABBIT_EVENT_BUS_CLASS.equals(bus.getClass().getName())
        && !environment.getProperty(RABBIT_CONFIRMS_PROPERTY, Boolean.class, false);
  }
}
