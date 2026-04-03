package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

class CqrsSerializationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsSerializationAutoConfiguration.class));

  @Test
  void jacksonMessageSerializerIsCreatedWhenObjectMapperIsAvailable() {
    contextRunner
        .withUserConfiguration(ObjectMapperConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JacksonMessageSerializer.class);
              assertThat(context).hasSingleBean(MessageSerializer.class);
              assertThat(context.getBean(MessageSerializer.class))
                  .isInstanceOf(JacksonMessageSerializer.class);
            });
  }

  @Test
  void jacksonMessageSerializerIsNotCreatedWhenObjectMapperClassIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(ObjectMapper.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JacksonMessageSerializer.class);
              assertThat(context).doesNotHaveBean(MessageSerializer.class);
            });
  }

  @Test
  void contextFailsWhenObjectMapperClassPresentButNoBeanExists() {
    contextRunner.run(
        context -> {
          assertThat(context).hasFailed();
        });
  }

  @Test
  void customMessageSerializerReplacesJacksonSerializer() {
    contextRunner
        .withUserConfiguration(ObjectMapperConfiguration.class)
        .withUserConfiguration(CustomSerializerConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MessageSerializer.class);
              assertThat(context.getBean(MessageSerializer.class))
                  .isInstanceOf(CustomMessageSerializer.class);
              assertThat(context).doesNotHaveBean(JacksonMessageSerializer.class);
            });
  }

  static class CustomMessageSerializer implements MessageSerializer {
    @Override
    public byte[] serialize(Object message) {
      return new byte[0];
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> type) {
      return null;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ObjectMapperConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomSerializerConfiguration {
    @Bean
    MessageSerializer messageSerializer() {
      return new CustomMessageSerializer();
    }
  }
}
