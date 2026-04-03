package com.borjaglez.cqrs.rabbitmq.aot;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqCommandConsumer;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqEventConsumer;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqQueryConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;

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
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
  }
}
