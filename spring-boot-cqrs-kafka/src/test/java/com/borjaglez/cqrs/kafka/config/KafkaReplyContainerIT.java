package com.borjaglez.cqrs.kafka.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mock.env.MockEnvironment;

import com.borjaglez.cqrs.kafka.infrastructure.DefaultKafkaTopicNamingStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

/** Broker-backed checks of where the reply container starts reading the reply topic. */
@EmbeddedKafka(
    partitions = 1,
    topics = {KafkaReplyContainerIT.RESTART_TOPIC, KafkaReplyContainerIT.MULTI_INSTANCE_TOPIC})
class KafkaReplyContainerIT {

  static final String RESTART_APP = "reply-restart-app";
  static final String RESTART_TOPIC = "cqrs." + RESTART_APP + ".replies";
  static final String MULTI_INSTANCE_APP = "reply-multi-app";
  static final String MULTI_INSTANCE_TOPIC = "cqrs." + MULTI_INSTANCE_APP + ".replies";

  private static final long RECEIVE_TIMEOUT_SECONDS = 30;

  private final KafkaCqrsAutoConfiguration configuration = new KafkaCqrsAutoConfiguration();
  private final KafkaTopicNamingStrategy namingStrategy =
      new DefaultKafkaTopicNamingStrategy("cqrs");
  private final List<ConcurrentMessageListenerContainer<String, byte[]>> containers =
      new ArrayList<>();
  private final List<DefaultKafkaProducerFactory<String, byte[]>> producerFactories =
      new ArrayList<>();

  @AfterEach
  void stopContainers() {
    containers.forEach(ConcurrentMessageListenerContainer::stop);
    producerFactories.forEach(DefaultKafkaProducerFactory::destroy);
  }

  @Test
  void replyContainerSkipsOldRepliesAndReceivesRepliesProducedAfterStart(EmbeddedKafkaBroker broker)
      throws Exception {
    KafkaTemplate<String, byte[]> template = template(broker);
    // A reply left in the topic by an earlier run of the application.
    send(template, RESTART_TOPIC, "old-reply", System.currentTimeMillis() - 60_000);

    BlockingQueue<String> received = new LinkedBlockingQueue<>();
    ConcurrentMessageListenerContainer<String, byte[]> container =
        replyContainer(broker, RESTART_APP, received);
    // Produced after the instance started but before the consumer has its partitions.
    send(template, RESTART_TOPIC, "new-reply", null);
    start(container);

    assertThat(received.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("new-reply");
    assertThat(received).isEmpty();
  }

  @Test
  void everyInstanceOfTheApplicationReceivesEveryReply(EmbeddedKafkaBroker broker)
      throws Exception {
    KafkaTemplate<String, byte[]> template = template(broker);
    BlockingQueue<String> firstInstance = new LinkedBlockingQueue<>();
    BlockingQueue<String> secondInstance = new LinkedBlockingQueue<>();
    ConcurrentMessageListenerContainer<String, byte[]> first =
        replyContainer(broker, MULTI_INSTANCE_APP, firstInstance);
    ConcurrentMessageListenerContainer<String, byte[]> second =
        replyContainer(broker, MULTI_INSTANCE_APP, secondInstance);
    assertThat(first.getContainerProperties().getGroupId())
        .isNotEqualTo(second.getContainerProperties().getGroupId());
    start(first);
    start(second);

    send(template, MULTI_INSTANCE_TOPIC, "shared-reply", null);

    assertThat(firstInstance.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .isEqualTo("shared-reply");
    assertThat(secondInstance.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .isEqualTo("shared-reply");
  }

  private ConcurrentMessageListenerContainer<String, byte[]> replyContainer(
      EmbeddedKafkaBroker broker, String applicationName, BlockingQueue<String> received) {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("spring.kafka.bootstrap-servers", broker.getBrokersAsString());
    ConsumerFactory<String, byte[]> consumerFactory =
        configuration.cqrsKafkaConsumerFactory(mock(ListableBeanFactory.class), environment);
    KafkaRequestReplyClient client = mock(KafkaRequestReplyClient.class);
    doAnswer(
            invocation -> {
              ConsumerRecord<String, byte[]> reply = invocation.getArgument(0);
              received.add(new String(reply.value(), UTF_8));
              return null;
            })
        .when(client)
        .handleReply(any());
    ConcurrentMessageListenerContainer<String, byte[]> container =
        configuration.cqrsKafkaReplyContainer(
            consumerFactory,
            client,
            new KafkaCqrsProperties(),
            namingStrategy,
            applicationName,
            false);
    containers.add(container);
    return container;
  }

  private static void start(ConcurrentMessageListenerContainer<String, byte[]> container)
      throws InterruptedException {
    container.start();
    long deadline = System.nanoTime() + Duration.ofSeconds(RECEIVE_TIMEOUT_SECONDS).toNanos();
    while (container.getAssignedPartitions().isEmpty() && System.nanoTime() < deadline) {
      TimeUnit.MILLISECONDS.sleep(50);
    }
    assertThat(container.getAssignedPartitions()).isNotEmpty();
  }

  private KafkaTemplate<String, byte[]> template(EmbeddedKafkaBroker broker) {
    DefaultKafkaProducerFactory<String, byte[]> producerFactory =
        new DefaultKafkaProducerFactory<>(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class));
    producerFactories.add(producerFactory);
    return new KafkaTemplate<>(producerFactory);
  }

  private static void send(
      KafkaTemplate<String, byte[]> template, String topic, String value, Long timestamp)
      throws Exception {
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(topic, 0, timestamp, value, value.getBytes(UTF_8));
    record
        .headers()
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, value.getBytes(UTF_8)));
    template.send(record).get(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }
}
