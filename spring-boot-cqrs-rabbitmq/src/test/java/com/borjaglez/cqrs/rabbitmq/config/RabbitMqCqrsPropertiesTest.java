package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RabbitMqCqrsPropertiesTest {

  @Test
  void shouldHaveCorrectDefaults() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getPrefix()).isEqualTo("cqrs");
    assertThat(properties.getRetry()).isNotNull();
    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
    assertThat(properties.getRetry().getTtl()).isEqualTo(1000);
  }

  @Test
  void shouldHaveCorrectCommandDefaults() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();

    assertThat(properties.getCommands().getExchange()).isEqualTo("commands");
    assertThat(properties.getCommands().getConcurrentConsumers()).isEqualTo(10);
    assertThat(properties.getCommands().getMaxConcurrentConsumers()).isEqualTo(20);
  }

  @Test
  void shouldHaveCorrectEventDefaults() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();

    assertThat(properties.getEvents().getExchange()).isEqualTo("events");
    assertThat(properties.getEvents().getConcurrentConsumers()).isEqualTo(10);
    assertThat(properties.getEvents().getMaxConcurrentConsumers()).isEqualTo(20);
  }

  @Test
  void shouldHaveCorrectQueryDefaults() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();

    assertThat(properties.getQueries().getExchange()).isEqualTo("queries");
    assertThat(properties.getQueries().getConcurrentConsumers()).isEqualTo(10);
    assertThat(properties.getQueries().getMaxConcurrentConsumers()).isEqualTo(20);
  }

  @Test
  void shouldSetAndGetEnabled() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();
    properties.setEnabled(false);
    assertThat(properties.isEnabled()).isFalse();
  }

  @Test
  void shouldSetAndGetPrefix() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();
    properties.setPrefix("amj");
    assertThat(properties.getPrefix()).isEqualTo("amj");
  }

  @Test
  void shouldSetAndGetRetryProperties() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();
    RabbitMqCqrsProperties.RetryProperties retry = new RabbitMqCqrsProperties.RetryProperties();
    retry.setMaxAttempts(5);
    retry.setTtl(5000);
    properties.setRetry(retry);

    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
    assertThat(properties.getRetry().getTtl()).isEqualTo(5000);
  }

  @Test
  void shouldSetAndGetBusProperties() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();

    RabbitMqCqrsProperties.BusProperties busProps = new RabbitMqCqrsProperties.BusProperties();
    busProps.setExchange("custom-exchange");
    busProps.setConcurrentConsumers(5);
    busProps.setMaxConcurrentConsumers(15);

    properties.setCommands(busProps);

    assertThat(properties.getCommands().getExchange()).isEqualTo("custom-exchange");
    assertThat(properties.getCommands().getConcurrentConsumers()).isEqualTo(5);
    assertThat(properties.getCommands().getMaxConcurrentConsumers()).isEqualTo(15);
  }

  @Test
  void busPropertiesDefaultConstructorShouldHaveEmptyExchange() {
    RabbitMqCqrsProperties.BusProperties busProps = new RabbitMqCqrsProperties.BusProperties();
    assertThat(busProps.getExchange()).isEmpty();
    assertThat(busProps.getConcurrentConsumers()).isEqualTo(10);
    assertThat(busProps.getMaxConcurrentConsumers()).isEqualTo(20);
  }

  @Test
  void busPropertiesParameterizedConstructor() {
    RabbitMqCqrsProperties.BusProperties busProps =
        new RabbitMqCqrsProperties.BusProperties("my-exchange", 3, 6);
    assertThat(busProps.getExchange()).isEqualTo("my-exchange");
    assertThat(busProps.getConcurrentConsumers()).isEqualTo(3);
    assertThat(busProps.getMaxConcurrentConsumers()).isEqualTo(6);
  }

  @Test
  void shouldSetEvents() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();
    RabbitMqCqrsProperties.BusProperties events =
        new RabbitMqCqrsProperties.BusProperties("my-events", 2, 4);
    properties.setEvents(events);
    assertThat(properties.getEvents().getExchange()).isEqualTo("my-events");
  }

  @Test
  void shouldSetQueries() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();
    RabbitMqCqrsProperties.BusProperties queries =
        new RabbitMqCqrsProperties.BusProperties("my-queries", 1, 2);
    properties.setQueries(queries);
    assertThat(properties.getQueries().getExchange()).isEqualTo("my-queries");
  }
}
