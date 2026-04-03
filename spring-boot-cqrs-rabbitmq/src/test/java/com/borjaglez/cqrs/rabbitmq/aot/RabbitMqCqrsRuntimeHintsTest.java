package com.borjaglez.cqrs.rabbitmq.aot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqCommandConsumer;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqEventConsumer;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqQueryConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;

class RabbitMqCqrsRuntimeHintsTest {

  @Test
  void shouldRegisterRuntimeHintsForAllConsumersAndAdapter() {
    RuntimeHints hints = new RuntimeHints();
    RabbitMqCqrsRuntimeHints registrar = new RabbitMqCqrsRuntimeHints();

    registrar.registerHints(hints, getClass().getClassLoader());

    assertThat(
            RuntimeHintsPredicates.reflection().onType(RabbitMqCommandConsumer.class).test(hints))
        .isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(RabbitMqEventConsumer.class).test(hints))
        .isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(RabbitMqQueryConsumer.class).test(hints))
        .isTrue();
    assertThat(
            RuntimeHintsPredicates.reflection()
                .onType(ExtendedMessageListenerAdapter.class)
                .test(hints))
        .isTrue();
  }

  @Test
  void shouldRegisterCorrectMemberCategories() {
    RuntimeHints hints = new RuntimeHints();
    RabbitMqCqrsRuntimeHints registrar = new RabbitMqCqrsRuntimeHints();

    registrar.registerHints(hints, getClass().getClassLoader());

    TypeHint commandConsumerHint = hints.reflection().getTypeHint(RabbitMqCommandConsumer.class);
    assertThat(commandConsumerHint).isNotNull();
    assertThat(commandConsumerHint.getMemberCategories())
        .contains(
            MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
  }
}
