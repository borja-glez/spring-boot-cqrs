package com.borjaglez.cqrs.jdbc;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.borjaglez.cqrs.jdbc.outbox.OutboxStore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs.jdbc")
public class JdbcCqrsProperties {

  /**
   * When to create the library's tables (processed messages, outbox): embedded (embedded databases
   * only), always, never.
   */
  private InitializeSchema initializeSchema = InitializeSchema.EMBEDDED;

  private Idempotency idempotency = new Idempotency();

  private Outbox outbox = new Outbox();

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

  @Getter
  @Setter
  public static class Outbox {
    /** Outbox table; may be schema-qualified. */
    private String tableName = OutboxStore.DEFAULT_TABLE_NAME;

    /** Whether to delete outbox rows published longer than cqrs.outbox.retention ago. */
    private boolean cleanupEnabled = true;

    /** Delay between two cleanups of published outbox rows. */
    private Duration cleanupInterval = Duration.ofHours(1);
  }
}
