package com.borjaglez.cqrs.kafka.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs.kafka")
public class KafkaCqrsProperties {

  private boolean enabled = true;
  private String prefix = "cqrs";
  private boolean autoCreateTopics = true;
  private PartitionKeyProperties partitionKey = new PartitionKeyProperties();
  private ReplyProperties replies = new ReplyProperties();
  private BusProperties commands = new BusProperties("commands", 3, (short) 1, 1, "");
  private BusProperties events = new BusProperties("events", 3, (short) 1, 1, "");
  private BusProperties queries = new BusProperties("queries", 3, (short) 1, 1, "");

  /**
   * Retry and dead-letter handling of the commands, events and queries listener containers. Ignored
   * when the application defines its own {@code CommonErrorHandler} bean.
   */
  private ErrorHandlingProperties errorHandling = new ErrorHandlingProperties();

  @Getter
  @Setter
  public static class ErrorHandlingProperties {

    /**
     * Total number of deliveries of a failed record, including the first one. The default {@code 3}
     * means the first attempt plus two retries; {@code 1} sends a failed record straight to the
     * dead-letter topic. Must be at least 1.
     */
    private int maxAttempts = 3;

    private BackOffProperties backOff = new BackOffProperties();
    private DeadLetterProperties deadLetter = new DeadLetterProperties();
  }

  @Getter
  @Setter
  public static class BackOffProperties {

    /** Delay before the first retry. */
    private Duration initialInterval = Duration.ofSeconds(1);

    /** Factor applied to the delay after every retry. Must be at least 1. */
    private double multiplier = 2.0;

    /** Upper bound of the delay between two attempts. */
    private Duration maxInterval = Duration.ofSeconds(10);
  }

  @Getter
  @Setter
  public static class DeadLetterProperties {

    /**
     * Whether a record that exhausts its attempts, or cannot be processed at all, is published to
     * the dead-letter topic of this application ({@code <prefix>.<application>.<bus>.dlt}). When
     * disabled it is logged and skipped.
     */
    private boolean enabled = true;

    /** Partitions of the dead-letter topics created when {@code auto-create-topics} is on. */
    private int partitions = 1;

    /** Replicas of the dead-letter topics created when {@code auto-create-topics} is on. */
    private short replicas = 1;
  }

  @Getter
  @Setter
  public static class PartitionKeyProperties {

    private PartitionKeyStrategyType strategy = PartitionKeyStrategyType.MESSAGE_NAME;
  }

  public enum PartitionKeyStrategyType {
    MESSAGE_NAME,
    PAYLOAD_TYPE,
    NONE
  }

  @Getter
  @Setter
  public static class ReplyProperties {

    private String topic = "replies";
    private int partitions = 1;
    private short replicas = 1;
    private Duration timeout = Duration.ofSeconds(30);
  }

  @Getter
  @Setter
  public static class BusProperties {

    /** Whether this bus uses Kafka. Disable commands and queries to use Kafka for events only. */
    private boolean enabled = true;

    private String topic;
    private int partitions;
    private short replicas;
    private int concurrency;
    private String groupId;

    public BusProperties() {
      this("", 3, (short) 1, 1, "");
    }

    public BusProperties(
        String topic, int partitions, short replicas, int concurrency, String groupId) {
      this.topic = topic;
      this.partitions = partitions;
      this.replicas = replicas;
      this.concurrency = concurrency;
      this.groupId = groupId;
    }
  }
}
