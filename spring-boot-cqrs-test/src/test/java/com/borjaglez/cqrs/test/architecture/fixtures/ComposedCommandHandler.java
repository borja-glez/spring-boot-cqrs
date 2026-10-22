package com.borjaglez.cqrs.test.architecture.fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.borjaglez.cqrs.command.annotation.CommandHandler;

/** A composed annotation: handler classes carry it instead of {@code @CommandHandler}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@CommandHandler
public @interface ComposedCommandHandler {}
