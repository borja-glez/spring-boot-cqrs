package com.borjaglez.cqrs.kafka.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Matches when commands or queries travel over Kafka, the only buses that need replies. An
 * application that uses Kafka for events only does not start the reply consumer.
 */
class RequestReplyBusEnabled extends AnyNestedCondition {

  RequestReplyBusEnabled() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnBooleanProperty(name = "cqrs.kafka.commands.enabled", matchIfMissing = true)
  interface CommandsEnabled {}

  @ConditionalOnBooleanProperty(name = "cqrs.kafka.queries.enabled", matchIfMissing = true)
  interface QueriesEnabled {}
}
