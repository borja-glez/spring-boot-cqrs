package com.borjaglez.cqrs.kafka.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.kafka.KafkaCommandBus;
import com.borjaglez.cqrs.kafka.KafkaEventBus;
import com.borjaglez.cqrs.kafka.config.KafkaCommandBusAutoConfiguration;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsAutoConfiguration;
import com.borjaglez.cqrs.kafka.config.KafkaEventBusAutoConfiguration;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Broker-backed checks of the retry and dead-letter handling of the Kafka consumers: a record that
 * keeps failing is retried the configured number of times and then published to the dead-letter
 * topic of the failing application, with the {@code cqrs.error.*} headers.
 */
@EmbeddedKafka(partitions = 1)
class KafkaErrorHandlingIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "spring.application.name=billing",
              "cqrs.kafka.prefix=error-it",
              "cqrs.kafka.queries.enabled=false",
              "cqrs.kafka.error-handling.back-off.initial-interval=50ms",
              "cqrs.kafka.error-handling.back-off.max-interval=100ms")
          .withBean(
              ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()))
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class,
                  CqrsSerializationAutoConfiguration.class,
                  KafkaAutoConfiguration.class,
                  KafkaCqrsAutoConfiguration.class,
                  KafkaCommandBusAutoConfiguration.class,
                  KafkaEventBusAutoConfiguration.class));

  @Test
  void failingEventEndsUpInTheApplicationsDeadLetterTopicAfterTheConfiguredAttempts(
      EmbeddedKafkaBroker broker) {
    AtomicInteger attempts = new AtomicInteger();
    contextRunner
        .withPropertyValues(
            "spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
            "cqrs.kafka.events.topic=failing-events")
        .withBean(FailingProjector.class, () -> new FailingProjector(attempts))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              Instant before = Instant.now();

              context.getBean(KafkaEventBus.class).publish(new OrderPlaced("order-1"));

              ConsumerRecord<String, byte[]> deadLetter =
                  singleRecord(broker, "error-it.billing.failing-events.dlt");
              assertThat(attempts).hasValue(3);
              assertThat(header(deadLetter, KafkaMessageHeaders.PAYLOAD_TYPE))
                  .isEqualTo(OrderPlaced.class.getName());
              assertThat(header(deadLetter, KafkaMessageHeaders.ERROR_TYPE))
                  .isEqualTo(IllegalStateException.class.getName());
              assertThat(header(deadLetter, KafkaMessageHeaders.ERROR_MESSAGE))
                  .isEqualTo("db down");
              assertThat(header(deadLetter, KafkaMessageHeaders.ERROR_ATTEMPTS)).isEqualTo("3");
              assertThat(Instant.parse(header(deadLetter, KafkaMessageHeaders.ERROR_TIMESTAMP)))
                  .isAfterOrEqualTo(before.minusSeconds(1));
              assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
              assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                  .isEqualTo("error-it.failing-events");
              assertThat(new String(deadLetter.value(), UTF_8)).contains("order-1");
            });
  }

  @Test
  void recordWithoutPayloadTypeIsDeadLetteredWithoutRetries(EmbeddedKafkaBroker broker) {
    AtomicInteger attempts = new AtomicInteger();
    contextRunner
        .withPropertyValues(
            "spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
            "cqrs.kafka.events.topic=untyped-events")
        .withBean(FailingProjector.class, () -> new FailingProjector(attempts))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              @SuppressWarnings("unchecked")
              KafkaTemplate<String, byte[]> template =
                  context.getBean("cqrsKafkaTemplate", KafkaTemplate.class);

              template
                  .send(
                      new ProducerRecord<>("error-it.untyped-events", "key", "{}".getBytes(UTF_8)))
                  .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

              ConsumerRecord<String, byte[]> deadLetter =
                  singleRecord(broker, "error-it.billing.untyped-events.dlt");
              assertThat(header(deadLetter, KafkaMessageHeaders.ERROR_ATTEMPTS)).isEqualTo("1");
              assertThat(header(deadLetter, KafkaMessageHeaders.ERROR_MESSAGE))
                  .isEqualTo("Missing Kafka CQRS payload type header");
              assertThat(attempts).hasValue(0);
            });
  }

  @Test
  void requestReplyFailureIsAnsweredAndNotDeadLettered(EmbeddedKafkaBroker broker) {
    AtomicInteger attempts = new AtomicInteger();
    contextRunner
        .withPropertyValues(
            "spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
            "cqrs.kafka.commands.topic=failing-commands",
            "cqrs.kafka.events.enabled=false")
        .withBean(FailingCommandHandler.class, () -> new FailingCommandHandler(attempts))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              KafkaCommandBus commandBus = context.getBean(KafkaCommandBus.class);

              assertThatThrownBy(() -> commandBus.dispatchAndReceive(new ChargeCard("card-1")))
                  .hasMessageContaining("card declined");

              assertThat(attempts).hasValue(1);
              assertThat(records(broker, "error-it.billing.failing-commands.dlt", 2)).isEmpty();
            });
  }

  private static ConsumerRecord<String, byte[]> singleRecord(
      EmbeddedKafkaBroker broker, String topic) {
    List<ConsumerRecord<String, byte[]>> records = records(broker, topic, TIMEOUT.toSeconds());
    assertThat(records).as("records in %s", topic).hasSize(1);
    return records.get(0);
  }

  private static List<ConsumerRecord<String, byte[]>> records(
      EmbeddedKafkaBroker broker, String topic, long timeoutSeconds) {
    Map<String, Object> properties =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            broker.getBrokersAsString(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "dlt-reader-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest");
    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    try (KafkaConsumer<String, byte[]> consumer =
        new KafkaConsumer<>(properties, new StringDeserializer(), new ByteArrayDeserializer())) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
      while (records.isEmpty() && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(200)).forEach(records::add);
      }
      // Anything published right after the first record would be a duplicate.
      consumer.poll(Duration.ofMillis(500)).forEach(records::add);
    }
    return records;
  }

  private static String header(ConsumerRecord<String, byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), UTF_8);
  }

  @Getter
  @NoArgsConstructor
  @CqrsMessage(service = "error-it", module = "orders", name = "order-placed")
  public static class OrderPlaced extends Event {

    private String orderId;

    public OrderPlaced(String orderId) {
      this.orderId = orderId;
    }
  }

  @EventHandler
  public static class FailingProjector {

    private final AtomicInteger attempts;

    FailingProjector(AtomicInteger attempts) {
      this.attempts = attempts;
    }

    @HandleEvent
    public void on(OrderPlaced event) {
      attempts.incrementAndGet();
      throw new IllegalStateException("db down");
    }
  }

  @Getter
  @NoArgsConstructor
  @CqrsMessage(service = "error-it", module = "billing", name = "charge-card")
  public static class ChargeCard extends Command {

    private String cardId;

    public ChargeCard(String cardId) {
      this.cardId = cardId;
    }
  }

  @CommandHandler
  public static class FailingCommandHandler {

    private final AtomicInteger attempts;

    FailingCommandHandler(AtomicInteger attempts) {
      this.attempts = attempts;
    }

    @HandleCommand
    public String handle(ChargeCard command) {
      attempts.incrementAndGet();
      throw new IllegalStateException("card declined");
    }
  }
}
