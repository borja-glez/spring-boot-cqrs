package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.lang.reflect.Constructor;
import java.util.List;

import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.util.ClassUtils;

public final class JsonMessageConverterFactory {

  private static final String JACKSON_3_OBJECT_MAPPER = "tools.jackson.databind.ObjectMapper";
  private static final String JACKSON_2_OBJECT_MAPPER =
      "com.fasterxml.jackson.databind.ObjectMapper";

  static final List<ConverterCandidate> DEFAULT_CONVERTERS =
      List.of(
          new ConverterCandidate(
              "org.springframework.amqp.support.converter.JacksonJsonMessageConverter",
              JACKSON_3_OBJECT_MAPPER),
          new ConverterCandidate(
              "org.springframework.amqp.support.converter.Jackson2JsonMessageConverter",
              JACKSON_2_OBJECT_MAPPER));

  private static final String[] TRUST_ALL = {"*"};

  private JsonMessageConverterFactory() {}

  /** A converter that trusts every package, as Spring AMQP 3 did by default. */
  public static MessageConverter create() {
    return create(TRUST_ALL);
  }

  /**
   * A converter that only deserializes types from the given packages ({@code "*"} for all). Spring
   * AMQP 4 trusts only {@code java.util} and {@code java.lang} by default, which rejects every
   * command, event and query.
   */
  public static MessageConverter create(String... trustedPackages) {
    return create(
        JsonMessageConverterFactory.class.getClassLoader(), DEFAULT_CONVERTERS, trustedPackages);
  }

  static MessageConverter create(
      ClassLoader classLoader, List<ConverterCandidate> converterCandidates) {
    return create(classLoader, converterCandidates, TRUST_ALL);
  }

  static MessageConverter create(
      ClassLoader classLoader,
      List<ConverterCandidate> converterCandidates,
      String... trustedPackages) {
    for (ConverterCandidate converterCandidate : converterCandidates) {
      if (isPresent(converterCandidate.converterClassName(), classLoader)
          && isPresent(converterCandidate.objectMapperClassName(), classLoader)) {
        return instantiate(converterCandidate.converterClassName(), classLoader, trustedPackages);
      }
    }

    throw new IllegalStateException(
        "No compatible Spring AMQP JSON message converter found. "
            + "Expected JacksonJsonMessageConverter (Spring AMQP 4 / Jackson 3) "
            + "or Jackson2JsonMessageConverter (Spring AMQP 3 / Jackson 2).");
  }

  private static MessageConverter instantiate(
      String converterClassName, ClassLoader classLoader, String... trustedPackages) {
    try {
      Class<?> converterClass = ClassUtils.forName(converterClassName, classLoader);
      // Both Spring AMQP converters take the trusted packages in their constructor.
      Constructor<?> constructor = converterClass.getDeclaredConstructor(String[].class);
      Object instance = constructor.newInstance((Object) trustedPackages.clone());
      return (MessageConverter) instance;
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Failed to instantiate Spring AMQP message converter: " + converterClassName, ex);
    }
  }

  private static boolean isPresent(String className, ClassLoader classLoader) {
    return ClassUtils.isPresent(className, classLoader);
  }

  record ConverterCandidate(String converterClassName, String objectMapperClassName) {}
}
