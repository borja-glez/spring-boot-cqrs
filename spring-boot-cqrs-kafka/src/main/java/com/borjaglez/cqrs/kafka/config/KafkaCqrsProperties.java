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
