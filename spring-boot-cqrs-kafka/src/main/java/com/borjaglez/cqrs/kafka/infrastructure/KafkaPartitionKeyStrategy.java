package com.borjaglez.cqrs.kafka.infrastructure;

public interface KafkaPartitionKeyStrategy {

  String partitionKey(KafkaMessageKind messageKind, Object message);
}
