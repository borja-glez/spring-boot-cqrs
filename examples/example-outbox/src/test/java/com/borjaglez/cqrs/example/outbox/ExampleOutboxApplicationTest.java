package com.borjaglez.cqrs.example.outbox;

import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.example.outbox.command.CreateOrderCommand;
import com.borjaglez.cqrs.example.outbox.event.OrderEventHandler;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ExampleOutboxApplicationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container @ServiceConnection
  static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

  @Autowired private CommandBus commandBus;
  @Autowired private OrderEventHandler orderEventHandler;

  @Test
  void aCreatedOrderReachesKafkaThroughTheOutbox() {
    String orderId = commandBus.dispatchAndReceive(new CreateOrderCommand("book", 2));

    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> orderEventHandler.received().contains(orderId));
  }
}
