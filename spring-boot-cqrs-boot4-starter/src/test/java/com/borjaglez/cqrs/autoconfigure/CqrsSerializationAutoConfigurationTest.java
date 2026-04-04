package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.serialization.Jackson3MessageSerializer;
import com.borjaglez.cqrs.serialization.MessageSerializer;

import tools.jackson.databind.json.JsonMapper;

class CqrsSerializationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsSerializationAutoConfiguration.class));

  @Test
  void jacksonMessageSerializerIsCreatedWhenJsonMapperIsAvailable() {
    contextRunner
        .withUserConfiguration(JsonMapperConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(Jackson3MessageSerializer.class);
              assertThat(context).hasSingleBean(MessageSerializer.class);
              assertThat(context.getBean(MessageSerializer.class))
                  .isInstanceOf(Jackson3MessageSerializer.class);
            });
  }

  @Test
  void jacksonMessageSerializerIsNotCreatedWhenJsonMapperClassIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(JsonMapper.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(Jackson3MessageSerializer.class);
              assertThat(context).doesNotHaveBean(MessageSerializer.class);
            });
  }

  @Test
  void serializerIsNotCreatedWhenJsonMapperClassPresentButNoBeanExists() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(Jackson3MessageSerializer.class);
          assertThat(context).doesNotHaveBean(MessageSerializer.class);
        });
  }

  @Test
  void customMessageSerializerReplacesJacksonSerializer() {
    contextRunner
        .withUserConfiguration(JsonMapperConfiguration.class)
        .withUserConfiguration(CustomSerializerConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MessageSerializer.class);
              assertThat(context.getBean(MessageSerializer.class))
                  .isInstanceOf(CustomMessageSerializer.class);
              assertThat(context).doesNotHaveBean(Jackson3MessageSerializer.class);
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
  static class JsonMapperConfiguration {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
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
