package com.borjaglez.cqrs.kafka.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsAutoConfiguration;
import com.borjaglez.cqrs.kafka.config.KafkaEventBusAutoConfiguration;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Broker-backed check that a consumer reads an event by its {@code @CqrsMessage} name: the
 * producer's class (named in {@code cqrs.payload.type}) does not exist in this application, as
 * after the producer renamed or moved it.
 */
@EmbeddedKafka(partitions = 1)
class KafkaMessageNameResolutionIT {

  @Test
  void eventFromARenamedProducerClassReachesTheLocalHandler(EmbeddedKafkaBroker broker) {
    AtomicReference<String> received = new AtomicReference<>();
    CountDownLatch handled = new CountDownLatch(1);
    new ApplicationContextRunner()
        .withPropertyValues(
            "spring.application.name=catalog-reader",
            "spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
            "cqrs.kafka.prefix=name-it",
            "cqrs.kafka.commands.enabled=false",
            "cqrs.kafka.queries.enabled=false")
        .withBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()))
        .withBean(
            ProductPublishedHandler.class, () -> new ProductPublishedHandler(received, handled))
        .withConfiguration(
            AutoConfigurations.of(
                CqrsAutoConfiguration.class,
                CqrsSerializationAutoConfiguration.class,
                KafkaAutoConfiguration.class,
                KafkaCqrsAutoConfiguration.class,
                KafkaEventBusAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              @SuppressWarnings("unchecked")
              KafkaTemplate<String, byte[]> template =
                  context.getBean("cqrsKafkaTemplate", KafkaTemplate.class);
              byte[] payload =
                  context.getBean(MessageSerializer.class).serialize(new ProductPublished("A-1"));
              String name =
                  context.getBean(MessageNamingStrategy.class).eventName(ProductPublished.class);

              ProducerRecord<String, byte[]> record =
                  new ProducerRecord<>("name-it.events", "key", payload);
              record
                  .headers()
                  .add(new RecordHeader(KafkaMessageHeaders.MESSAGE_KIND, "EVENT".getBytes(UTF_8)))
                  .add(new RecordHeader(KafkaMessageHeaders.MESSAGE_NAME, name.getBytes(UTF_8)))
                  .add(
                      new RecordHeader(
                          KafkaMessageHeaders.PAYLOAD_TYPE,
                          "com.example.producer.catalog.ProductPublishedEvent".getBytes(UTF_8)));
              template.send(record).get(30, TimeUnit.SECONDS);

              assertThat(handled.await(30, TimeUnit.SECONDS)).isTrue();
              assertThat(received).hasValue("A-1");
            });
  }

  @Getter
  @NoArgsConstructor
  @CqrsMessage(service = "catalog", module = "product", name = "product-published")
  public static class ProductPublished extends Event {

    private String sku;

    public ProductPublished(String sku) {
      this.sku = sku;
    }
  }

  @EventHandler
  public static class ProductPublishedHandler {

    private final AtomicReference<String> received;
    private final CountDownLatch handled;

    ProductPublishedHandler(AtomicReference<String> received, CountDownLatch handled) {
      this.received = received;
      this.handled = handled;
    }

    @HandleEvent
    public void on(ProductPublished event) {
      received.set(event.getSku());
      handled.countDown();
    }
  }
}
