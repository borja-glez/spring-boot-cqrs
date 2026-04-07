package com.borjaglez.cqrs.kafka.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;

class DefaultKafkaPartitionKeyStrategyTest {

  private MessageNamingStrategy messageNamingStrategy;
  private KafkaCqrsProperties properties;

  @BeforeEach
  void setUp() {
    messageNamingStrategy = mock(MessageNamingStrategy.class);
    properties = new KafkaCqrsProperties();
  }

  @Test
  void shouldUseMessageNameByDefault() {
    Command command = new TestCommand("value");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.order.create");

    DefaultKafkaPartitionKeyStrategy strategy =
        new DefaultKafkaPartitionKeyStrategy(properties, messageNamingStrategy);

    assertThat(strategy.partitionKey(KafkaMessageKind.COMMAND, command))
        .isEqualTo("sales.order.create");
  }

  @Test
  void shouldResolveEventMessageNameWhenConfiguredByDefault() {
    when(messageNamingStrategy.eventName(TestEvent.class)).thenReturn("sales.order.created");

    DefaultKafkaPartitionKeyStrategy strategy =
        new DefaultKafkaPartitionKeyStrategy(properties, messageNamingStrategy);

    assertThat(strategy.partitionKey(KafkaMessageKind.EVENT, new TestEvent("value")))
        .isEqualTo("sales.order.created");
  }

  @Test
  void shouldResolveQueryMessageNameWhenConfiguredByDefault() {
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");

    DefaultKafkaPartitionKeyStrategy strategy =
        new DefaultKafkaPartitionKeyStrategy(properties, messageNamingStrategy);

    assertThat(strategy.partitionKey(KafkaMessageKind.QUERY, new TestQuery("value")))
        .isEqualTo("sales.order.find");
  }

  @Test
  void shouldUsePayloadTypeWhenConfigured() {
    properties
        .getPartitionKey()
        .setStrategy(KafkaCqrsProperties.PartitionKeyStrategyType.PAYLOAD_TYPE);

    DefaultKafkaPartitionKeyStrategy strategy =
        new DefaultKafkaPartitionKeyStrategy(properties, messageNamingStrategy);

    assertThat(strategy.partitionKey(KafkaMessageKind.COMMAND, new TestCommand("value")))
        .isEqualTo(TestCommand.class.getName());
  }

  @Test
  void shouldReturnNullWhenDisabled() {
    properties.getPartitionKey().setStrategy(KafkaCqrsProperties.PartitionKeyStrategyType.NONE);

    DefaultKafkaPartitionKeyStrategy strategy =
        new DefaultKafkaPartitionKeyStrategy(properties, messageNamingStrategy);

    assertThat(strategy.partitionKey(KafkaMessageKind.COMMAND, new TestCommand("value"))).isNull();
  }
}
