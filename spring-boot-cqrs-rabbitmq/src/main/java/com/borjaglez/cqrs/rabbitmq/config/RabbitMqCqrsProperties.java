package com.borjaglez.cqrs.rabbitmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs.rabbitmq")
public class RabbitMqCqrsProperties {

  private boolean enabled = true;
  private String prefix = "cqrs";
  private RetryProperties retry = new RetryProperties();
  private BusProperties commands = new BusProperties("commands", 10, 20);
  private BusProperties events = new BusProperties("events", 10, 20);
  private BusProperties queries = new BusProperties("queries", 10, 20);

  @Getter
  @Setter
  public static class RetryProperties {

    private int maxAttempts = 3;
    private long ttl = 1000;
  }

  @Getter
  @Setter
  public static class BusProperties {

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
