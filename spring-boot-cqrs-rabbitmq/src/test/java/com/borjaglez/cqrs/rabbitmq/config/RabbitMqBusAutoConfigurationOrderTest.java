package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.core.annotation.MergedAnnotations;

import com.borjaglez.cqrs.autoconfigure.CqrsHandlersAutoConfiguration;

/**
 * The bus auto-configurations build their declarations from the handler registries when their beans
 * are created. They come after the handler auto-configuration because they say so, not because of
 * the alphabetical order of the class names.
 */
class RabbitMqBusAutoConfigurationOrderTest {

  @ParameterizedTest
  @ValueSource(
      classes = {
        RabbitMqCommandBusAutoConfiguration.class,
        RabbitMqQueryBusAutoConfiguration.class,
        RabbitMqEventBusAutoConfiguration.class
      })
  void comesAfterTheHandlerAutoConfiguration(Class<?> autoConfiguration) {
    assertThat(after(autoConfiguration)).contains(CqrsHandlersAutoConfiguration.class.getName());
  }

  /**
   * The auto-configurations an auto-configuration comes after, whether it names them with
   * {@code @AutoConfigureAfter} or with {@code @AutoConfiguration}.
   */
  private static Set<String> after(Class<?> autoConfiguration) {
    return MergedAnnotations.from(autoConfiguration).stream(AutoConfigureAfter.class)
        .flatMap(
            after ->
                Stream.concat(
                    Stream.of(after.getStringArray("name")),
                    Stream.of(after.getClassArray("value")).map(Class::getName)))
        .collect(Collectors.toSet());
  }
}
