package com.borjaglez.cqrs.kafka.infrastructure;

public interface KafkaTopicNamingStrategy {

  String topic(String logicalName);

  String replyTopic(String applicationName, String logicalName);
}
