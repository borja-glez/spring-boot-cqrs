package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ClassUtils;

/**
 * Creates a {@link RabbitTemplate} configured like Spring Boot's auto-configured one, with its own
 * reply timeout.
 *
 * <p>The template goes through Boot's {@code RabbitTemplateConfigurer} bean, so it gets the same
 * {@code spring.rabbitmq.template.*} settings (message converter, mandatory, retry, observation,
 * receive timeout, exchange and routing key), and then through every {@code
 * RabbitTemplateCustomizer} bean, in order, as Boot's {@code rabbitTemplate} does. Only the reply
 * timeout differs. Both types live in {@code org.springframework.boot.autoconfigure.amqp} on Spring
 * Boot 3 and in {@code org.springframework.boot.amqp.autoconfigure} on Spring Boot 4, so they are
 * looked up by name.
 */
public final class RabbitTemplateFactory {

  static final List<BootTemplateTypes> BOOT_TEMPLATE_TYPES =
      List.of(
          // Spring Boot 4
          new BootTemplateTypes(
              "org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer",
              "org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer"),
          // Spring Boot 3
          new BootTemplateTypes(
              "org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer",
              "org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer"));

  private RabbitTemplateFactory() {}

  /**
   * A template configured like Boot's {@code rabbitTemplate} bean, with the given reply timeout.
   *
   * @throws IllegalStateException when Spring Boot's {@code RabbitTemplateConfigurer} is not on the
   *     classpath or not a bean
   */
  public static RabbitTemplate create(
      BeanFactory beanFactory, ConnectionFactory connectionFactory, Duration replyTimeout) {
    return create(
        RabbitTemplateFactory.class.getClassLoader(),
        BOOT_TEMPLATE_TYPES,
        beanFactory,
        connectionFactory,
        replyTimeout);
  }

  static RabbitTemplate create(
      ClassLoader classLoader,
      List<BootTemplateTypes> candidates,
      BeanFactory beanFactory,
      ConnectionFactory connectionFactory,
      Duration replyTimeout) {
    BootTemplateTypes types =
        candidates.stream()
            .filter(candidate -> ClassUtils.isPresent(candidate.configurerClassName(), classLoader))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "A RabbitMQ reply timeout per bus needs Spring Boot's"
                            + " RabbitTemplateConfigurer, which is not on the classpath"));
    try {
      RabbitTemplate template = new RabbitTemplate();
      configure(types, classLoader, beanFactory, template, connectionFactory);
      customize(types, classLoader, beanFactory, template);
      template.setReplyTimeout(replyTimeout.toMillis());
      return template;
    } catch (InvocationTargetException ex) {
      if (ex.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(
          "Failed to configure the RabbitTemplate of a bus", ex.getCause());
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Failed to configure the RabbitTemplate of a bus", ex);
    }
  }

  private static void configure(
      BootTemplateTypes types,
      ClassLoader classLoader,
      BeanFactory beanFactory,
      RabbitTemplate template,
      ConnectionFactory connectionFactory)
      throws ReflectiveOperationException {
    Class<?> configurerType = ClassUtils.forName(types.configurerClassName(), classLoader);
    Object configurer = beanFactory.getBeanProvider(configurerType).getIfAvailable();
    if (configurer == null) {
      throw new IllegalStateException(
          "A RabbitMQ reply timeout per bus needs a "
              + configurerType.getName()
              + " bean, as Spring Boot's RabbitAutoConfiguration defines");
    }
    configurerType
        .getMethod("configure", RabbitTemplate.class, ConnectionFactory.class)
        .invoke(configurer, template, connectionFactory);
  }

  private static void customize(
      BootTemplateTypes types,
      ClassLoader classLoader,
      BeanFactory beanFactory,
      RabbitTemplate template)
      throws ReflectiveOperationException {
    Class<?> customizerType = ClassUtils.forName(types.customizerClassName(), classLoader);
    Method customize = customizerType.getMethod("customize", RabbitTemplate.class);
    for (Object customizer : beanFactory.getBeanProvider(customizerType).orderedStream().toList()) {
      customize.invoke(customizer, template);
    }
  }

  record BootTemplateTypes(String configurerClassName, String customizerClassName) {}
}
