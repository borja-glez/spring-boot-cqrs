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

  private JsonMessageConverterFactory() {}

  public static MessageConverter create() {
    return create(JsonMessageConverterFactory.class.getClassLoader(), DEFAULT_CONVERTERS);
  }

  static MessageConverter create(
      ClassLoader classLoader, List<ConverterCandidate> converterCandidates) {
    for (ConverterCandidate converterCandidate : converterCandidates) {
      if (isPresent(converterCandidate.converterClassName(), classLoader)
          && isPresent(converterCandidate.objectMapperClassName(), classLoader)) {
        return instantiate(converterCandidate.converterClassName(), classLoader);
      }
    }

    throw new IllegalStateException(
        "No compatible Spring AMQP JSON message converter found. "
            + "Expected JacksonJsonMessageConverter (Spring AMQP 4 / Jackson 3) "
            + "or Jackson2JsonMessageConverter (Spring AMQP 3 / Jackson 2).");
  }

  private static MessageConverter instantiate(String converterClassName, ClassLoader classLoader) {
    try {
      Class<?> converterClass = ClassUtils.forName(converterClassName, classLoader);
      Constructor<?> constructor = converterClass.getDeclaredConstructor();
      Object instance = constructor.newInstance();
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
