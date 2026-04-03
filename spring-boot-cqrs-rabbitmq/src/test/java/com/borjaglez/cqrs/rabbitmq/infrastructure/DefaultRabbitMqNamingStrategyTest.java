package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultRabbitMqNamingStrategyTest {

  @Test
  void exchangeShouldReturnPrefixedName() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("cqrs");
    assertThat(strategy.exchange("commands")).isEqualTo("cqrs.commands");
  }

  @Test
  void exchangeRetryShouldReturnPrefixedNameWithRetrySuffix() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("cqrs");
    assertThat(strategy.exchangeRetry("commands")).isEqualTo("cqrs.commands.retry");
  }

  @Test
  void exchangeDeadLetterShouldReturnPrefixedNameWithDeadLetterSuffix() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("cqrs");
    assertThat(strategy.exchangeDeadLetter("commands")).isEqualTo("cqrs.commands.dead_letter");
  }

  @Test
  void queueShouldReturnPrefixedAppAndExchangeName() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("cqrs");
    assertThat(strategy.queue("my-app", "commands")).isEqualTo("cqrs.my-app.commands");
  }

  @Test
  void queueRetryShouldReturnPrefixedAppAndExchangeNameWithRetrySuffix() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("cqrs");
    assertThat(strategy.queueRetry("my-app", "commands")).isEqualTo("cqrs.my-app.commands.retry");
  }

  @Test
  void queueDeadLetterShouldReturnPrefixedAppAndExchangeNameWithDeadLetterSuffix() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("cqrs");
    assertThat(strategy.queueDeadLetter("my-app", "commands"))
        .isEqualTo("cqrs.my-app.commands.dead_letter");
  }

  @Test
  void shouldHandleEmptyPrefix() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("");
    assertThat(strategy.exchange("commands")).isEqualTo(".commands");
    assertThat(strategy.exchangeRetry("commands")).isEqualTo(".commands.retry");
    assertThat(strategy.exchangeDeadLetter("commands")).isEqualTo(".commands.dead_letter");
    assertThat(strategy.queue("app", "commands")).isEqualTo(".app.commands");
    assertThat(strategy.queueRetry("app", "commands")).isEqualTo(".app.commands.retry");
    assertThat(strategy.queueDeadLetter("app", "commands")).isEqualTo(".app.commands.dead_letter");
  }

  @Test
  void shouldHandleCustomPrefix() {
    DefaultRabbitMqNamingStrategy strategy = new DefaultRabbitMqNamingStrategy("amj");
    assertThat(strategy.exchange("events")).isEqualTo("amj.events");
    assertThat(strategy.queue("server", "events")).isEqualTo("amj.server.events");
  }
}
