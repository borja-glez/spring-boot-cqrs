package com.borjaglez.cqrs.aot;

import java.lang.annotation.Annotation;
import java.util.List;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

public class CqrsRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

  private static final List<Class<? extends Annotation>> ANNOTATION_TYPES =
      List.of(
          CommandHandler.class,
          HandleCommand.class,
          EventHandler.class,
          HandleEvent.class,
          QueryHandler.class,
          HandleQuery.class,
          CqrsMessage.class);

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    for (Class<? extends Annotation> annotationType : ANNOTATION_TYPES) {
      hints.reflection().registerType(annotationType, MemberCategory.values());
    }
  }
}
