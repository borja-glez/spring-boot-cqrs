package com.borjaglez.cqrs.jdbc.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs.outbox")
public class OutboxProperties {

  /** Whether to store events published through OutboxEventBus and relay them after commit. */
  private boolean enabled;

  /** How long published rows are kept before the cleanup deletes them. */
  private Duration retention = Duration.ofDays(7);

  private Relay relay = new Relay();

  @Getter
  @Setter
  public static class Relay {

    /** Whether this instance relays pending rows; instances that only write can turn it off. */
    private boolean enabled = true;

    /**
     * Bean name of the EventBus the relay publishes through. Unset: the only event bus other than
     * springEventBus and the outbox bus (kafkaEventBus or rabbitMqEventBus).
     */
    private String eventBus;

    /** Delay between two relay runs. */
    private Duration interval = Duration.ofSeconds(1);

    /** Rows relayed per transaction. */
    private int batchSize = 100;

    /**
     * Failed reads of a row (unknown class, unreadable payload or stored context) after which it is
     * set aside. Failures of the event bus are not counted and never set a row aside.
     */
    private int maxAttempts = 10;
  }
}
