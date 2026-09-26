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

  @Test
  void deadLetterTopicBelongsToTheApplicationAndTheBus() {
    assertThat(new DefaultKafkaTopicNamingStrategy("cqrs").deadLetterTopic("orders", "events"))
        .isEqualTo("cqrs.orders.events.dlt");
    assertThat(new DefaultKafkaTopicNamingStrategy("").deadLetterTopic("orders", "commands"))
        .isEqualTo("orders.commands.dlt");
  }

  @Test
  void customStrategiesInheritTheDeadLetterTopicNaming() {
    KafkaTopicNamingStrategy custom =
        new KafkaTopicNamingStrategy() {
          @Override
          public String topic(String logicalName) {
            return "acme-" + logicalName;
          }

          @Override
          public String replyTopic(String applicationName, String logicalName) {
            return topic(applicationName + "-" + logicalName);
          }
        };

    assertThat(custom.deadLetterTopic("orders", "queries")).isEqualTo("acme-orders.queries.dlt");
  }
}
