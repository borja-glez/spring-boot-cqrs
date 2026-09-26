package com.borjaglez.cqrs.kafka.infrastructure;

public interface KafkaTopicNamingStrategy {

  String topic(String logicalName);

  String replyTopic(String applicationName, String logicalName);

  /**
   * Name of the dead-letter topic of one application for one bus: {@code
   * <prefix>.<applicationName>.<logicalName>.dlt} with the default naming.
   *
   * <p>The commands, events and queries topics are shared by every service, each reading them with
   * its own consumer group, so a dead-letter topic is never shared: replaying it must only reach
   * the application whose handler failed.
   *
   * @param applicationName the name of the application whose handler failed
   * @param logicalName the logical name of the bus topic ({@code commands}, {@code events}, ...)
   * @return the dead-letter topic name
   */
  default String deadLetterTopic(String applicationName, String logicalName) {
    return topic(applicationName + "." + logicalName + ".dlt");
  }
}
