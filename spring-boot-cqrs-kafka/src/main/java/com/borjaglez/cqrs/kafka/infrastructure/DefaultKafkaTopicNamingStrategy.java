package com.borjaglez.cqrs.kafka.infrastructure;

public class DefaultKafkaTopicNamingStrategy implements KafkaTopicNamingStrategy {

  private final String prefix;

  public DefaultKafkaTopicNamingStrategy(String prefix) {
    this.prefix = prefix == null ? "" : prefix;
  }

  @Override
  public String topic(String logicalName) {
    if (prefix.isBlank()) {
      return logicalName;
    }
    return prefix + "." + logicalName;
  }

  @Override
  public String replyTopic(String applicationName, String logicalName) {
    return topic(applicationName + "." + logicalName);
  }
}
