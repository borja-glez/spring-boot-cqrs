package com.borjaglez.cqrs.kafka.infrastructure;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;

/**
 * Keys a command or event by the key it declares through {@link com.borjaglez.cqrs.KeyedMessage},
 * so all the messages of one entity keep their order; any other message, or one declaring a {@code
 * null} or blank key, is keyed as {@code cqrs.kafka.partition-key.strategy} says.
 */
public class DefaultKafkaPartitionKeyStrategy implements KafkaPartitionKeyStrategy {

  private final KafkaCqrsProperties.PartitionKeyStrategyType strategyType;
  private final MessageNamingStrategy messageNamingStrategy;

  public DefaultKafkaPartitionKeyStrategy(
      KafkaCqrsProperties properties, MessageNamingStrategy messageNamingStrategy) {
    this.strategyType = properties.getPartitionKey().getStrategy();
    this.messageNamingStrategy = messageNamingStrategy;
  }

  @Override
  public String partitionKey(KafkaMessageKind messageKind, Object message) {
    String declaredKey = KafkaMessageKeys.declaredKey(messageKind, message);
    if (declaredKey != null) {
      return declaredKey;
    }
    return switch (strategyType) {
      case NONE -> null;
      case PAYLOAD_TYPE -> message.getClass().getName();
      case MESSAGE_NAME -> resolveMessageName(messageKind, message);
    };
  }

  private String resolveMessageName(KafkaMessageKind messageKind, Object message) {
    return switch (messageKind) {
      case COMMAND -> messageNamingStrategy.commandName(((Command) message).getClass());
      case EVENT -> messageNamingStrategy.eventName(((Event) message).getClass());
      case QUERY -> messageNamingStrategy.queryName(((Query) message).getClass());
    };
  }
}
