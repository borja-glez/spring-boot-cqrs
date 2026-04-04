package com.borjaglez.cqrs.rabbitmq.aot;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.ClassUtils;

import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqCommandConsumer;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqEventConsumer;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqQueryConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.JsonMessageConverterFactory;

public class RabbitMqCqrsRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints
        .reflection()
        .registerType(
            RabbitMqCommandConsumer.class,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
        .registerType(
            RabbitMqEventConsumer.class,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
        .registerType(
            RabbitMqQueryConsumer.class,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
        .registerType(
            ExtendedMessageListenerAdapter.class,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
        .registerType(
            JsonMessageConverterFactory.class,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

    registerIfPresent(
        hints,
        classLoader,
        "org.springframework.amqp.support.converter.JacksonJsonMessageConverter");
    registerIfPresent(
        hints,
        classLoader,
        "org.springframework.amqp.support.converter.Jackson2JsonMessageConverter");
  }

  private void registerIfPresent(RuntimeHints hints, ClassLoader classLoader, String className) {
    if (ClassUtils.isPresent(className, classLoader)) {
      hints
          .reflection()
          .registerType(
              TypeReference.of(className),
              MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
              MemberCategory.INVOKE_PUBLIC_METHODS);
    }
  }
}
