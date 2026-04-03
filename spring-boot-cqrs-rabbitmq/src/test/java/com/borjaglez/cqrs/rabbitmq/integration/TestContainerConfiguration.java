package com.borjaglez.cqrs.rabbitmq.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;

@TestConfiguration
public class TestContainerConfiguration {

  @Bean
  @ServiceConnection
  public RabbitMQContainer rabbitMQContainer() {
    return new RabbitMQContainer("rabbitmq:3.13-management");
  }
}
