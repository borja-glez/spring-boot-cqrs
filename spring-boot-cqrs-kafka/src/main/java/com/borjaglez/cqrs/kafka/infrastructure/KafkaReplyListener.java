package com.borjaglez.cqrs.kafka.infrastructure;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.listener.MessageListener;

/**
 * Listener of the reply topic that only reads replies produced after the instance started.
 *
 * <p>Every instance consumes the reply topic with its own consumer group, so each one sees every
 * reply and keeps those whose correlation id it is waiting for. A new group has no committed
 * offset; instead of reading the whole retained topic, each assigned partition is positioned at the
 * first record written at or after {@code startTimestamp}. A reply produced after the start is
 * still read when it reaches the topic before the partitions are assigned.
 */
public class KafkaReplyListener implements MessageListener<String, byte[]>, ConsumerSeekAware {

  private final KafkaRequestReplyClient requestReplyClient;
  private final long startTimestamp;

  public KafkaReplyListener(KafkaRequestReplyClient requestReplyClient, long startTimestamp) {
    this.requestReplyClient = requestReplyClient;
    this.startTimestamp = startTimestamp;
  }

  @Override
  public void onMessage(ConsumerRecord<String, byte[]> reply) {
    requestReplyClient.handleReply(reply);
  }

  @Override
  public void onPartitionsAssigned(
      Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
    callback.seekToTimestamp(assignments.keySet(), startTimestamp);
  }
}
