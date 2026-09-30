package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;

class JsonMessageConverterFactoryTest {

  @Test
  void createShouldPreferFirstCompatibleConverterCandidate() {
    MessageConverter converter =
        JsonMessageConverterFactory.create(
            JsonMessageConverterFactory.class.getClassLoader(),
            List.of(
                new JsonMessageConverterFactory.ConverterCandidate(
                    TestMessageConverter.class.getName(), TestObjectMapper.class.getName()),
                new JsonMessageConverterFactory.ConverterCandidate(
                    "org.springframework.amqp.support.converter.Jackson2JsonMessageConverter",
                    "com.fasterxml.jackson.databind.ObjectMapper")));

    assertThat(converter).isInstanceOf(TestMessageConverter.class);
  }

  @Test
  void createShouldSkipCandidateWhenObjectMapperIsMissing() {
    MessageConverter converter =
        JsonMessageConverterFactory.create(
            JsonMessageConverterFactory.class.getClassLoader(),
            List.of(
                new JsonMessageConverterFactory.ConverterCandidate(
                    TestMessageConverter.class.getName(), "com.example.MissingObjectMapper"),
                new JsonMessageConverterFactory.ConverterCandidate(
                    "org.springframework.amqp.support.converter.Jackson2JsonMessageConverter",
                    "com.fasterxml.jackson.databind.ObjectMapper")));

    assertThat(converter)
        .extracting(created -> created.getClass().getName())
        .isEqualTo("org.springframework.amqp.support.converter.Jackson2JsonMessageConverter");
  }

  @Test
  void createShouldUseBoot3CompatibleConverterByDefaultInThisModule() {
    MessageConverter converter = JsonMessageConverterFactory.create();

    assertThat(converter)
        .extracting(created -> created.getClass().getName())
        .isEqualTo("org.springframework.amqp.support.converter.Jackson2JsonMessageConverter");
  }

  @Test
  void createShouldPassTheTrustedPackages() {
    MessageConverter converter =
        JsonMessageConverterFactory.create(
            JsonMessageConverterFactory.class.getClassLoader(),
            List.of(
                new JsonMessageConverterFactory.ConverterCandidate(
                    TestMessageConverter.class.getName(), TestObjectMapper.class.getName())),
            "com.example.contracts");

    assertThat(((TestMessageConverter) converter).trustedPackages)
        .containsExactly("com.example.contracts");
  }

  @Test
  void byDefaultEveryPackageIsTrusted() {
    MessageConverter converter =
        JsonMessageConverterFactory.create(
            JsonMessageConverterFactory.class.getClassLoader(),
            List.of(
                new JsonMessageConverterFactory.ConverterCandidate(
                    TestMessageConverter.class.getName(), TestObjectMapper.class.getName())));

    assertThat(((TestMessageConverter) converter).trustedPackages).containsExactly("*");
  }

  @Test
  void onlyTrustedPackagesAreDeserialized() {
    MessageConverter trusting = JsonMessageConverterFactory.create();
    MessageConverter restricted = JsonMessageConverterFactory.create("com.example.contracts");
    Message message = trusting.toMessage(new TestCommand("data"), new MessageProperties());

    assertThat(trusting.fromMessage(message)).isInstanceOf(TestCommand.class);
    assertThatThrownBy(() -> restricted.fromMessage(message))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not in the trusted packages");
  }

  @Test
  void createShouldUseTheMapperFoundForTheSelectedConverter() {
    TestObjectMapper mapper = new TestObjectMapper();

    MessageConverter converter =
        JsonMessageConverterFactory.create(
            JsonMessageConverterFactory.class.getClassLoader(),
            List.of(
                new JsonMessageConverterFactory.ConverterCandidate(
                    TestMessageConverter.class.getName(), TestObjectMapper.class.getName())),
            mapperType -> mapperType == TestObjectMapper.class ? mapper : null,
            "com.example.contracts");

    assertThat(((TestMessageConverter) converter).mapper).isSameAs(mapper);
    assertThat(((TestMessageConverter) converter).trustedPackages)
        .containsExactly("com.example.contracts");
  }

  @Test
  void createShouldUseSpringAmqpMapperWhenNoMapperIsFound() {
    MessageConverter converter =
        JsonMessageConverterFactory.create(
            JsonMessageConverterFactory.class.getClassLoader(),
            List.of(
                new JsonMessageConverterFactory.ConverterCandidate(
                    TestMessageConverter.class.getName(), TestObjectMapper.class.getName())),
            mapperType -> null,
            "com.example.contracts");

    assertThat(((TestMessageConverter) converter).mapper).isNull();
    assertThat(((TestMessageConverter) converter).trustedPackages)
        .containsExactly("com.example.contracts");
  }

  @Test
  void createWithBeanFactoryShouldKeepTheDefaultConverterWithoutMapperBean() {
    MessageConverter converter =
        JsonMessageConverterFactory.create(
            new StaticListableBeanFactory(), "com.example.contracts");
    Message message =
        JsonMessageConverterFactory.create()
            .toMessage(new TestCommand("data"), new MessageProperties());

    assertThat(converter)
        .extracting(created -> created.getClass().getName())
        .isEqualTo("org.springframework.amqp.support.converter.Jackson2JsonMessageConverter");
    assertThatThrownBy(() -> converter.fromMessage(message))
        .hasMessageContaining("not in the trusted packages");
  }

  @Test
  void createShouldFailWhenNoCompatibleConverterExists() {
    assertThatThrownBy(
            () ->
                JsonMessageConverterFactory.create(
                    JsonMessageConverterFactory.class.getClassLoader(),
                    List.of(
                        new JsonMessageConverterFactory.ConverterCandidate(
                            "com.example.Missing", "com.example.MissingObjectMapper"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No compatible Spring AMQP JSON message converter found");
  }

  @Test
  void createShouldFailWhenCompatibleClassCannotBeInstantiated() {
    assertThatThrownBy(
            () ->
                JsonMessageConverterFactory.create(
                    JsonMessageConverterFactory.class.getClassLoader(),
                    List.of(
                        new JsonMessageConverterFactory.ConverterCandidate(
                            InvalidMessageConverter.class.getName(),
                            TestObjectMapper.class.getName()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to instantiate Spring AMQP message converter");
  }

  static class TestObjectMapper {}

  static class TestMessageConverter implements MessageConverter {

    final TestObjectMapper mapper;
    final String[] trustedPackages;

    TestMessageConverter(String... trustedPackages) {
      this(null, trustedPackages);
    }

    TestMessageConverter(TestObjectMapper mapper, String... trustedPackages) {
      this.mapper = mapper;
      this.trustedPackages = trustedPackages;
    }

    @Override
    public Message toMessage(Object object, MessageProperties messageProperties)
        throws MessageConversionException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
      throw new UnsupportedOperationException();
    }
  }

  static class InvalidMessageConverter implements MessageConverter {
    InvalidMessageConverter(String ignored) {}

    @Override
    public Message toMessage(Object object, MessageProperties messageProperties)
        throws MessageConversionException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
      throw new UnsupportedOperationException();
    }
  }
}
