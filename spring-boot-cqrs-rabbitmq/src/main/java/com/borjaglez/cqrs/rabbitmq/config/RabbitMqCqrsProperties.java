package com.borjaglez.cqrs.rabbitmq.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs.rabbitmq")
public class RabbitMqCqrsProperties {

  private boolean enabled = true;
  private String prefix = "cqrs";

  /**
   * Packages whose types may be deserialized from incoming messages, {@code "*"} for all. Limit it
   * to the packages of your messages and results when the broker is shared with untrusted
   * producers.
   */
  private List<String> trustedPackages = new ArrayList<>(List.of("*"));

  private RetryProperties retry = new RetryProperties();
  private BusProperties commands = new BusProperties("commands", 10, 20);
  private BusProperties events = new BusProperties("events", 10, 20);
  private BusProperties queries = new BusProperties("queries", 10, 20);

  @Getter
  @Setter
  public static class RetryProperties {

    /**
     * Total number of deliveries of a failed asynchronous command or event, including the first
     * one. The default {@code 3} means the first attempt plus two retries; {@code 1} sends a failed
     * message straight to the dead-letter queue. Must be at least 1.
     */
    private int maxAttempts = 3;

    /** Delay in milliseconds a failed message waits in the retry queue before it is retried. */
    private long ttl = 1000;
  }

  @Getter
  @Setter
  public static class BusProperties {

    /**
     * Whether this bus uses RabbitMQ. Disabling it removes the bus bean, its queues and exchanges
     * and its listener container, so the application can neither send nor receive this kind of
     * message over RabbitMQ.
     */
    private boolean enabled = true;

    private String exchange;
    private int concurrentConsumers;
    private int maxConcurrentConsumers;

    public BusProperties() {
      this("", 10, 20);
    }

    public BusProperties(String exchange, int concurrentConsumers, int maxConcurrentConsumers) {
      this.exchange = exchange;
      this.concurrentConsumers = concurrentConsumers;
      this.maxConcurrentConsumers = maxConcurrentConsumers;
    }
  }
}
