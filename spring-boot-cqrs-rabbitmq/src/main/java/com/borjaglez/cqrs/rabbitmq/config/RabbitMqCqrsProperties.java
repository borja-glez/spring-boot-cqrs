package com.borjaglez.cqrs.rabbitmq.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqExposure;

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

  /**
   * Which handled messages are exposed over RabbitMQ: bound to this application's queues and
   * handled when they arrive from the broker. {@code annotated} (the default) exposes only messages
   * annotated with {@code @CqrsMessage}; {@code all} exposes every handled message. Handlers marked
   * {@code remote = false} are never exposed. Other messages stay local, and are rejected without
   * requeue if they reach a queue anyway.
   */
  private RabbitMqExposure expose = RabbitMqExposure.ANNOTATED;

  private RetryProperties retry = new RetryProperties();
  private BusProperties commands = new BusProperties("commands", 10, 20);
  private EventBusProperties events = new EventBusProperties("events", 10, 20);
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

    /**
     * How long this bus waits for the reply of a request ({@code dispatchAndReceive} on the command
     * bus, {@code ask} on the query bus) before it throws {@code RemoteReplyTimeoutException}. When
     * set, the bus sends through its own {@code RabbitTemplate}, configured like Spring Boot's with
     * the {@code spring.rabbitmq.template.*} settings and every {@code RabbitTemplateCustomizer},
     * and only the reply timeout changed. When unset, the bus uses the application's {@code
     * RabbitTemplate} and so {@code spring.rabbitmq.template.reply-timeout}. Ignored for events,
     * which have no reply.
     */
    private Duration replyTimeout;

    public BusProperties() {
      this("", 10, 20);
    }

    public BusProperties(String exchange, int concurrentConsumers, int maxConcurrentConsumers) {
      this.exchange = exchange;
      this.concurrentConsumers = concurrentConsumers;
      this.maxConcurrentConsumers = maxConcurrentConsumers;
    }
  }

  @Getter
  @Setter
  public static class EventBusProperties extends BusProperties {

    private ConfirmsProperties confirms = new ConfirmsProperties();

    public EventBusProperties() {
      super();
    }

    public EventBusProperties(
        String exchange, int concurrentConsumers, int maxConcurrentConsumers) {
      super(exchange, concurrentConsumers, maxConcurrentConsumers);
    }
  }

  @Getter
  @Setter
  public static class ConfirmsProperties {

    /**
     * Whether publishing an event waits for the broker to confirm it and fails when the broker
     * rejects it or does not confirm it within the timeout. Needs {@code
     * spring.rabbitmq.publisher-confirm-type=correlated}.
     */
    private boolean enabled = false;

    /** How long publishing an event waits for the broker confirmation. */
    private Duration timeout = Duration.ofSeconds(5);
  }
}
