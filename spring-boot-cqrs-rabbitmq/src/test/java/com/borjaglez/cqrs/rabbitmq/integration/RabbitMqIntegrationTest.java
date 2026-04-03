package com.borjaglez.cqrs.rabbitmq.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCqrsProperties;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQuery;

@SpringBootTest(
    classes = TestApplication.class,
    properties = {
      "spring.application.name=integration-test",
      "cqrs.rabbitmq.enabled=true",
      "cqrs.rabbitmq.prefix=test-cqrs"
    })
@Import(TestContainerConfiguration.class)
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class RabbitMqIntegrationTest {

  static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Autowired private RabbitMqCqrsProperties properties;

  @Autowired private CommandHandlerRegistry commandHandlerRegistry;

  @Autowired private EventHandlerRegistry eventHandlerRegistry;

  @Autowired private QueryHandlerRegistry queryHandlerRegistry;

  @Autowired private MessageNamingStrategy messageNamingStrategy;

  @Autowired(required = false)
  private RabbitMqCommandBus rabbitMqCommandBus;

  @Autowired(required = false)
  private RabbitMqEventBus rabbitMqEventBus;

  @Autowired(required = false)
  private RabbitMqQueryBus rabbitMqQueryBus;

  @Test
  void contextShouldLoadWithRabbitMqConfiguration() {
    assertThat(properties).isNotNull();
    assertThat(properties.getPrefix()).isEqualTo("test-cqrs");
  }

  @Test
  void registriesShouldBeAvailable() {
    assertThat(commandHandlerRegistry).isNotNull();
    assertThat(eventHandlerRegistry).isNotNull();
    assertThat(queryHandlerRegistry).isNotNull();
  }

  @Test
  void rabbitMqBusesShouldBeCreated() {
    assertThat(rabbitMqCommandBus).isNotNull();
    assertThat(rabbitMqEventBus).isNotNull();
    assertThat(rabbitMqQueryBus).isNotNull();
  }

  @Test
  void shouldDispatchCommandViaRabbitMq() {
    TestCommand command = new TestCommand("integration-test-data");

    rabbitMqCommandBus.dispatch(command);

    // Command handler registered by TestCommandHandler via auto-discovery
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              // Verify the command was handled by checking the registry has our command type
              assertThat(commandHandlerRegistry.getRegisteredCommands())
                  .contains(TestCommand.class);
            });
  }

  @Test
  void shouldPublishEventViaRabbitMq() {
    TestEvent event = new TestEvent("integration-event-data");

    rabbitMqEventBus.publish(event);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(eventHandlerRegistry.getRegisteredEvents()).contains(TestEvent.class);
            });
  }

  @Test
  void shouldAskQueryViaRabbitMq() {
    TestQuery query = new TestQuery("integration-query-data");

    String result = rabbitMqQueryBus.ask(query);

    assertThat(result).isEqualTo("result:integration-query-data");
  }
}
