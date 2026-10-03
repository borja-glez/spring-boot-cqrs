package com.borjaglez.cqrs.jdbc.outbox;

import java.sql.DatabaseMetaData;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

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
  private final DataSource dataSource;
  private final String table;
  private volatile String lockSql;
  private final String publishedSql;
  private final String readFailedSql;
  private final String publishFailedSql;
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
    this.dataSource = dataSource;
    this.table = table;
    this.publishedSql = "UPDATE " + table + " SET published_at = ? WHERE id = ?";
    this.readFailedSql =
        "UPDATE "
            + table
            + " SET attempts = ?, read_failures = ?, last_error = ?, failed_at = ? WHERE id = ?";
    this.publishFailedSql = "UPDATE " + table + " SET attempts = ?, last_error = ? WHERE id = ?";
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
        lockSql().formatted(batchSize),
        (rs, rowNum) ->
            new OutboxRecord(
                rs.getLong("id"),
                rs.getString("event_id"),
                rs.getString("event_name"),
                rs.getString("event_class"),
                rs.getBytes("payload"),
                rs.getBytes("context"),
                rs.getInt("attempts"),
                rs.getInt("read_failures")));
  }

  private String lockSql() {
    String sql = lockSql;
    if (sql == null) {
      try {
        String product =
            JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
        sql = lockSql(product, table);
      } catch (MetaDataAccessException e) {
        throw new IllegalStateException("Cannot detect the database for the outbox", e);
      }
      lockSql = sql;
    }
    return sql;
  }

  /**
   * The standard form is {@code ORDER BY id FETCH FIRST n ROWS ONLY FOR UPDATE SKIP LOCKED}. H2
   * locks every matching row before it applies the limit, so a relay would lock the whole backlog
   * and starve the others; for H2 the limit is applied in a subquery and only those ids are locked.
   */
  static String lockSql(String databaseProductName, String table) {
    String columns =
        "SELECT id, event_id, event_name, event_class, payload, context, attempts, read_failures"
            + " FROM ";
    String pending = " WHERE published_at IS NULL AND failed_at IS NULL ORDER BY id";
    if ("H2".equals(databaseProductName)) {
      return columns
          + table
          + " WHERE id IN (SELECT id FROM "
          + table
          + pending
          + " FETCH FIRST %d ROWS ONLY) ORDER BY id FOR UPDATE SKIP LOCKED";
    }
    return columns + table + pending + " FETCH FIRST %d ROWS ONLY FOR UPDATE SKIP LOCKED";
  }

  public void markPublished(long id) {
    jdbc.update(publishedSql, now(), id);
  }

  /**
   * Records an attempt that could not read the row, with the new total of failed attempts and of
   * failed reads. A row set aside ({@code setAside}) gets a {@code failed_at} and is no longer
   * relayed; otherwise it stays pending.
   */
  public void markReadFailed(
      long id, int attempts, int readFailures, String error, boolean setAside) {
    jdbc.update(
        readFailedSql,
        attempts,
        readFailures,
        truncate(error),
        new SqlParameterValue(Types.TIMESTAMP, setAside ? now() : null),
        id);
  }

  /**
   * Records an attempt the event bus failed, with the new total of failed attempts; it stays
   * pending.
   */
  public void markPublishFailed(long id, int attempts, String error) {
    jdbc.update(publishFailedSql, attempts, truncate(error), id);
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
