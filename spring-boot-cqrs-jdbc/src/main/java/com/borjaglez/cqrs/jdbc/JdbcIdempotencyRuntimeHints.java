package com.borjaglez.cqrs.jdbc;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/** Keeps the schema script that {@link JdbcIdempotencySchemaInitializer} reads in native images. */
public class JdbcIdempotencyRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints.resources().registerPattern(JdbcIdempotencySchemaInitializer.SCHEMA_LOCATION);
  }
}
