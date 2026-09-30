package com.borjaglez.cqrs.rabbitmq.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;

import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;
import com.borjaglez.cqrs.rabbitmq.fixtures.SlowCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.SlowQuery;

/**
 * Each bus waits for its reply as long as its own {@code reply-timeout} says, not the shared {@code
 * spring.rabbitmq.template.reply-timeout} (issue #114).
 */
@SpringBootTest(
    classes = TestApplication.class,
    properties = {
      "spring.application.name=reply-timeout-test",
      "cqrs.rabbitmq.prefix=reply-timeout-cqrs",
      "spring.rabbitmq.template.reply-timeout=200ms",
      "cqrs.rabbitmq.commands.reply-timeout=10s",
      "cqrs.rabbitmq.queries.reply-timeout=500ms"
    })
@Import(TestContainerConfiguration.class)
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class RabbitMqReplyTimeoutIntegrationTest {

  private static final long HANDLER_DELAY_MILLIS = 1500;

  static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Autowired private RabbitMqCommandBus rabbitMqCommandBus;

  @Autowired private RabbitMqQueryBus rabbitMqQueryBus;

  @Test
  void aQuerySlowerThanTheQueryReplyTimeoutTimesOut() {
    assertThatThrownBy(() -> rabbitMqQueryBus.ask(new SlowQuery(HANDLER_DELAY_MILLIS)))
        .isInstanceOf(RemoteReplyTimeoutException.class);
  }

  @Test
  void aCommandWithTheSameHandlerDelayGetsItsReply() {
    Object result = rabbitMqCommandBus.dispatchAndReceive(new SlowCommand(HANDLER_DELAY_MILLIS));

    assertThat(result).isNull();
  }

  @Test
  void aQueryFasterThanTheQueryReplyTimeoutGetsItsReply() {
    String result = rabbitMqQueryBus.ask(new SlowQuery(300));

    assertThat(result).isEqualTo("slow:300");
  }
}
