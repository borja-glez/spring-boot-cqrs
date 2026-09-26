package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqExposure;

class RabbitMqCqrsPropertiesTest {

  @Test
  void shouldHaveCorrectDefaults() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getPrefix()).isEqualTo("cqrs");
    assertThat(properties.getRetry()).isNotNull();
    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
    assertThat(properties.getRetry().getTtl()).isEqualTo(1000);
    assertThat(properties.getExpose()).isEqualTo(RabbitMqExposure.ANNOTATED);
  }

  @Test
  void shouldBindExpose() {
    RabbitMqCqrsProperties properties =
        new Binder(new MapConfigurationPropertySource(Map.of("cqrs.rabbitmq.expose", "all")))
            .bind("cqrs.rabbitmq", RabbitMqCqrsProperties.class)
            .get();

    assertThat(properties.getExpose()).isEqualTo(RabbitMqExposure.ALL);
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
    RabbitMqCqrsProperties.EventBusProperties events =
        new RabbitMqCqrsProperties.EventBusProperties("my-events", 2, 4);
    properties.setEvents(events);
    assertThat(properties.getEvents().getExchange()).isEqualTo("my-events");
  }

  @Test
  void eventConfirmsShouldBeDisabledByDefaultWithAFiveSecondTimeout() {
    RabbitMqCqrsProperties.EventBusProperties events = new RabbitMqCqrsProperties().getEvents();

    assertThat(events.getConfirms().isEnabled()).isFalse();
    assertThat(events.getConfirms().getTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(new RabbitMqCqrsProperties.EventBusProperties().getConfirms().isEnabled()).isFalse();
  }

  @Test
  void shouldBindEventConfirms() {
    RabbitMqCqrsProperties properties =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "cqrs.rabbitmq.events.confirms.enabled", "true",
                        "cqrs.rabbitmq.events.confirms.timeout", "250ms")))
            .bind("cqrs.rabbitmq", RabbitMqCqrsProperties.class)
            .get();

    assertThat(properties.getEvents().getConfirms().isEnabled()).isTrue();
    assertThat(properties.getEvents().getConfirms().getTimeout()).isEqualTo(Duration.ofMillis(250));
    assertThat(properties.getEvents().getExchange()).isEqualTo("events");
  }

  @Test
  void shouldSetQueries() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();
    RabbitMqCqrsProperties.BusProperties queries =
        new RabbitMqCqrsProperties.BusProperties("my-queries", 1, 2);
    properties.setQueries(queries);
    assertThat(properties.getQueries().getExchange()).isEqualTo("my-queries");
  }

  @Test
  void shouldEnableEveryBusByDefault() {
    RabbitMqCqrsProperties properties = new RabbitMqCqrsProperties();

    assertThat(properties.getCommands().isEnabled()).isTrue();
    assertThat(properties.getQueries().isEnabled()).isTrue();
    assertThat(properties.getEvents().isEnabled()).isTrue();
    assertThat(new RabbitMqCqrsProperties.BusProperties().isEnabled()).isTrue();
  }

  @Test
  void shouldBindTheEnabledFlagOfEachBus() {
    RabbitMqCqrsProperties properties =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "cqrs.rabbitmq.commands.enabled", "false",
                        "cqrs.rabbitmq.queries.enabled", "false",
                        "cqrs.rabbitmq.events.enabled", "true")))
            .bind("cqrs.rabbitmq", RabbitMqCqrsProperties.class)
            .get();

    assertThat(properties.getCommands().isEnabled()).isFalse();
    assertThat(properties.getQueries().isEnabled()).isFalse();
    assertThat(properties.getEvents().isEnabled()).isTrue();
    assertThat(properties.getCommands().getExchange()).isEqualTo("commands");
  }
}
