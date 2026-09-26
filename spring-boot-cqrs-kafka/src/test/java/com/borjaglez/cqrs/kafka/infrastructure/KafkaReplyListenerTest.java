package com.borjaglez.cqrs.kafka.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;

class KafkaReplyListenerTest {

  private final KafkaRequestReplyClient client = mock(KafkaRequestReplyClient.class);
  private final KafkaReplyListener listener = new KafkaReplyListener(client, 1_000L);

  @Test
  void seeksEveryAssignedPartitionToTheStartTimestamp() {
    ConsumerSeekCallback callback = mock(ConsumerSeekCallback.class);
    TopicPartition first = new TopicPartition("cqrs.app.replies", 0);
    TopicPartition second = new TopicPartition("cqrs.app.replies", 1);

    listener.onPartitionsAssigned(Map.of(first, 5L, second, 7L), callback);

    verify(callback).seekToTimestamp(Set.of(first, second), 1_000L);
    verifyNoInteractions(client);
  }

  @Test
  void delegatesRepliesToTheRequestReplyClient() {
    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.app.replies", 0, 0L, "key", new byte[0]);

    listener.onMessage(reply);

    verify(client).handleReply(reply);
  }
}
