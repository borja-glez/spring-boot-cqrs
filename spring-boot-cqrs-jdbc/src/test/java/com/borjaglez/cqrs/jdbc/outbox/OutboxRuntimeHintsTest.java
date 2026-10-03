package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class OutboxRuntimeHintsTest {

  @Test
  void keepsBothSchemaScripts() {
    RuntimeHints hints = new RuntimeHints();

    new OutboxRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(
            RuntimeHintsPredicates.resource()
                .forResource(OutboxSchemaInitializer.POSTGRESQL_SCHEMA_LOCATION))
        .accepts(hints);
    assertThat(
            RuntimeHintsPredicates.resource()
                .forResource(OutboxSchemaInitializer.H2_SCHEMA_LOCATION))
        .accepts(hints);
  }
}
