package com.borjaglez.cqrs.jdbc;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs.jdbc")
public class JdbcCqrsProperties {

  /** When to create the library's tables: embedded (embedded databases only), always, never. */
  private InitializeSchema initializeSchema = InitializeSchema.EMBEDDED;

  private Idempotency idempotency = new Idempotency();

  public enum InitializeSchema {
    EMBEDDED,
    ALWAYS,
    NEVER
  }

  @Getter
  @Setter
  public static class Idempotency {
    /** Table of processed messages; may be schema-qualified. */
    private String tableName = JdbcIdempotencyStore.DEFAULT_TABLE_NAME;

    /** Whether to delete processed-message markers older than cqrs.idempotency.retention. */
    private boolean cleanupEnabled = true;

    /** Delay between two cleanups. */
    private Duration cleanupInterval = Duration.ofHours(1);
  }
}
