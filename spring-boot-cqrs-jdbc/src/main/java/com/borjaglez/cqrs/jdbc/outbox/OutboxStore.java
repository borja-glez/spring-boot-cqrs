package com.borjaglez.cqrs.jdbc.outbox;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

import com.borjaglez.cqrs.jdbc.JdbcIdempotencyStore;

/**
 * The outbox table. {@link #insert} joins the caller's transaction (the {@link JdbcTemplate} uses
 * the connection bound to it, also under a JPA transaction manager). {@link #lockBatch} locks the
 * oldest pending rows with {@code FOR UPDATE SKIP LOCKED}, so it must run inside a transaction:
 * rows locked by another relay are skipped, never returned twice.
 */
public class OutboxStore {

  public static final String DEFAULT_TABLE_NAME = "cqrs_outbox";

  static final int MAX_ERROR_LENGTH = 2000;

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final String insertSql;
  private final String lockSql;
  private final String publishedSql;
  private final String failedSql;
  private final String deleteSql;

  public OutboxStore(DataSource dataSource, String tableName) {
    this(dataSource, tableName, Clock.systemUTC());
  }

  public OutboxStore(DataSource dataSource, String tableName, Clock clock) {
    String table = JdbcIdempotencyStore.validTableName(tableName);
    this.jdbc = new JdbcTemplate(dataSource);
    this.clock = clock;
    this.insertSql =
        "INSERT INTO "
            + table
            + " (event_id, event_name, event_class, payload, context, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)";
    this.lockSql =
        "SELECT id, event_id, event_name, event_class, payload, context, attempts FROM "
            + table
            + " WHERE id IN (SELECT id FROM "
            + table
            + " WHERE published_at IS NULL AND failed_at IS NULL ORDER BY id"
            + " FETCH FIRST %d ROWS ONLY) ORDER BY id FOR UPDATE SKIP LOCKED";
    this.publishedSql = "UPDATE " + table + " SET published_at = ? WHERE id = ?";
    this.failedSql =
        "UPDATE " + table + " SET attempts = ?, last_error = ?, failed_at = ? WHERE id = ?";
    this.deleteSql = "DELETE FROM " + table + " WHERE published_at < ?";
  }

  public void insert(
      String eventId, String eventName, String eventClass, byte[] payload, byte[] context) {
    jdbc.update(
        insertSql,
        eventId,
        eventName,
        eventClass,
        new SqlParameterValue(Types.VARBINARY, payload),
        new SqlParameterValue(Types.VARBINARY, context),
        now());
  }

  /** Locks and returns up to {@code batchSize} pending rows, oldest first. */
  public List<OutboxRecord> lockBatch(int batchSize) {
    return jdbc.query(
        lockSql.formatted(batchSize),
        (rs, rowNum) ->
            new OutboxRecord(
                rs.getLong("id"),
                rs.getString("event_id"),
                rs.getString("event_name"),
                rs.getString("event_class"),
                rs.getBytes("payload"),
                rs.getBytes("context"),
                rs.getInt("attempts")));
  }

  public void markPublished(long id) {
    jdbc.update(publishedSql, now(), id);
  }

  /**
   * Records a failed attempt. A row set aside ({@code setAside}) gets a {@code failed_at} and is no
   * longer relayed; otherwise it stays pending.
   */
  public void markFailed(long id, int attempts, String error, boolean setAside) {
    jdbc.update(
        failedSql,
        attempts,
        truncate(error),
        new SqlParameterValue(Types.TIMESTAMP, setAside ? now() : null),
        id);
  }

  /** Deletes the rows published before {@code cutoff}; returns how many. */
  public int deletePublishedBefore(Instant cutoff) {
    return jdbc.update(deleteSql, Timestamp.from(cutoff));
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private static String truncate(String error) {
    if (error == null || error.length() <= MAX_ERROR_LENGTH) {
      return error;
    }
    return error.substring(0, MAX_ERROR_LENGTH);
  }
}
