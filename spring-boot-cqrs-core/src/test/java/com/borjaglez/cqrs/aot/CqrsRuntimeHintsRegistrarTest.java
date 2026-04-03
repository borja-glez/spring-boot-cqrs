package com.borjaglez.cqrs.aot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

class CqrsRuntimeHintsRegistrarTest {

  @Test
  void allAnnotationsAreRegistered() {
    RuntimeHints hints = new RuntimeHints();
    CqrsRuntimeHintsRegistrar registrar = new CqrsRuntimeHintsRegistrar();
    registrar.registerHints(hints, getClass().getClassLoader());

    assertThat(RuntimeHintsPredicates.reflection().onType(CommandHandler.class).test(hints))
        .isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(HandleCommand.class).test(hints))
        .isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(EventHandler.class).test(hints)).isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(HandleEvent.class).test(hints)).isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(QueryHandler.class).test(hints)).isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(HandleQuery.class).test(hints)).isTrue();
    assertThat(RuntimeHintsPredicates.reflection().onType(CqrsMessage.class).test(hints)).isTrue();
  }
}
