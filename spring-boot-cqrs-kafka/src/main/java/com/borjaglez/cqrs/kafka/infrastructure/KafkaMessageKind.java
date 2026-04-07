package com.borjaglez.cqrs.kafka.infrastructure;

public enum KafkaMessageKind {
  COMMAND,
  EVENT,
  QUERY
}
