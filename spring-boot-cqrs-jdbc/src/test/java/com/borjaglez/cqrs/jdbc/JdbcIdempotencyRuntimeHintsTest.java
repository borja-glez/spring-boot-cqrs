package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;

class JdbcIdempotencyRuntimeHintsTest {

  @Test
  void registersTheSchemaScript() {
    RuntimeHints hints = new RuntimeHints();

    new JdbcIdempotencyRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(
            RuntimeHintsPredicates.resource()
                .forResource(JdbcIdempotencySchemaInitializer.SCHEMA_LOCATION)
                .test(hints))
        .isTrue();
  }

  @Test
  void theAutoConfigurationImportsTheHints() {
    assertThat(
            CqrsJdbcIdempotencyAutoConfiguration.class
                .getAnnotation(ImportRuntimeHints.class)
                .value())
        .containsExactly(JdbcIdempotencyRuntimeHints.class);
  }
}
