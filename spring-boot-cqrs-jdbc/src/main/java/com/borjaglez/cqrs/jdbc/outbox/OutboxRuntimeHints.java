package com.borjaglez.cqrs.jdbc.outbox;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/** Keeps the outbox schema scripts that {@link OutboxSchemaInitializer} reads in native images. */
public class OutboxRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints.resources().registerPattern(OutboxSchemaInitializer.POSTGRESQL_SCHEMA_LOCATION);
    hints.resources().registerPattern(OutboxSchemaInitializer.H2_SCHEMA_LOCATION);
  }
}
