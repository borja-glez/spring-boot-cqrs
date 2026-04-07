package com.borjaglez.cqrs.kafka.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultKafkaTopicNamingStrategyTest {

  @Test
  void topicReturnsLogicalNameWhenPrefixIsNull() {
    DefaultKafkaTopicNamingStrategy strategy = new DefaultKafkaTopicNamingStrategy(null);

    assertThat(strategy.topic("commands")).isEqualTo("commands");
  }

  @Test
  void topicReturnsLogicalNameWhenPrefixIsBlank() {
    DefaultKafkaTopicNamingStrategy strategy = new DefaultKafkaTopicNamingStrategy("   ");

    assertThat(strategy.topic("commands")).isEqualTo("commands");
  }

  @Test
  void topicPrefixesLogicalNameWhenPrefixIsConfigured() {
    DefaultKafkaTopicNamingStrategy strategy = new DefaultKafkaTopicNamingStrategy("cqrs");

    assertThat(strategy.topic("commands")).isEqualTo("cqrs.commands");
    assertThat(strategy.replyTopic("orders", "replies")).isEqualTo("cqrs.orders.replies");
  }
}
