package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.util.List;
import java.util.function.Function;

import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ClassUtils;

public final class JsonMessageConverterFactory {

  private static final String JACKSON_3_JSON_MAPPER = "tools.jackson.databind.json.JsonMapper";
  private static final String JACKSON_2_OBJECT_MAPPER =
      "com.fasterxml.jackson.databind.ObjectMapper";

  static final List<ConverterCandidate> DEFAULT_CONVERTERS =
      List.of(
          new ConverterCandidate(
              "org.springframework.amqp.support.converter.JacksonJsonMessageConverter",
              JACKSON_3_JSON_MAPPER),
          new ConverterCandidate(
              "org.springframework.amqp.support.converter.Jackson2JsonMessageConverter",
              JACKSON_2_OBJECT_MAPPER));

  private static final String[] TRUST_ALL = {"*"};

  private static final Function<Class<?>, Object> NO_MAPPER = mapperType -> null;

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

  /**
   * Like {@link #create(String...)}, but the converter writes and reads JSON with the application's
   * Jackson mapper when the bean factory has a unique (or primary) one of the generation the
   * converter supports: {@code tools.jackson.databind.json.JsonMapper} for Jackson 3 or {@code
   * com.fasterxml.jackson.databind.ObjectMapper} for Jackson 2. RabbitMQ messages then share the
   * format and customizations of the rest of the application. Without such a mapper, Spring AMQP's
   * own is used.
   */
  public static MessageConverter create(BeanFactory beanFactory, String... trustedPackages) {
    return create(
        JsonMessageConverterFactory.class.getClassLoader(),
        DEFAULT_CONVERTERS,
        mapperType -> beanFactory.getBeanProvider(mapperType).getIfUnique(),
        trustedPackages);
  }

  static MessageConverter create(
      ClassLoader classLoader, List<ConverterCandidate> converterCandidates) {
    return create(classLoader, converterCandidates, TRUST_ALL);
  }

  static MessageConverter create(
      ClassLoader classLoader,
      List<ConverterCandidate> converterCandidates,
      String... trustedPackages) {
    return create(classLoader, converterCandidates, NO_MAPPER, trustedPackages);
  }

  static MessageConverter create(
      ClassLoader classLoader,
      List<ConverterCandidate> converterCandidates,
      Function<Class<?>, Object> mapperLookup,
      String... trustedPackages) {
    for (ConverterCandidate converterCandidate : converterCandidates) {
      if (isPresent(converterCandidate.converterClassName(), classLoader)
          && isPresent(converterCandidate.objectMapperClassName(), classLoader)) {
        return instantiate(converterCandidate, classLoader, mapperLookup, trustedPackages);
      }
    }

    throw new IllegalStateException(
        "No compatible Spring AMQP JSON message converter found. "
            + "Expected JacksonJsonMessageConverter (Spring AMQP 4 / Jackson 3) "
            + "or Jackson2JsonMessageConverter (Spring AMQP 3 / Jackson 2).");
  }

  private static MessageConverter instantiate(
      ConverterCandidate converterCandidate,
      ClassLoader classLoader,
      Function<Class<?>, Object> mapperLookup,
      String... trustedPackages) {
    String converterClassName = converterCandidate.converterClassName();
    try {
      Class<?> converterClass = ClassUtils.forName(converterClassName, classLoader);
      Class<?> mapperType =
          ClassUtils.forName(converterCandidate.objectMapperClassName(), classLoader);
      Object mapper = mapperLookup.apply(mapperType);
      // Both Spring AMQP converters take the trusted packages in their constructor, optionally
      // preceded by the mapper. The Jackson 3 one is not on this module's compile classpath.
      Object instance =
          mapper == null
              ? converterClass
                  .getDeclaredConstructor(String[].class)
                  .newInstance((Object) trustedPackages.clone())
              : converterClass
                  .getDeclaredConstructor(mapperType, String[].class)
                  .newInstance(mapper, trustedPackages.clone());
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
