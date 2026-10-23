package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Import;

/**
 * Registers as beans the handler classes of the application packages, so a class annotated with
 * {@code @CommandHandler}, {@code @QueryHandler} or {@code @EventHandler} needs no stereotype of
 * its own. Turned off with {@code cqrs.handlers.scan-enabled=false}.
 */
@AutoConfiguration
@ConditionalOnBooleanProperty(name = "cqrs.handlers.scan-enabled", matchIfMissing = true)
@Import(CqrsHandlersRegistrar.class)
public class CqrsHandlersAutoConfiguration {}
