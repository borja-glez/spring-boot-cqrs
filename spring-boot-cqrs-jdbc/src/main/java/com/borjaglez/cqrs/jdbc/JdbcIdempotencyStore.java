package com.borjaglez.cqrs.jdbc;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;

/**
 * {@link IdempotencyStore} backed by a table. {@link #runInScope} runs the handler in a transaction
 * (joining the caller's one when there is one) and {@link #tryAcquire} inserts the marker in it, so
 * the marker commits with the handler's database work and a rollback removes both. A concurrent
 * delivery of the same message blocks on the uncommitted row and then sees a duplicate key. The
 * insert runs in a savepoint so a duplicate does not abort the caller's transaction.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

  public static final String DEFAULT_TABLE_NAME = "cqrs_processed_message";

  private static final Pattern TABLE_NAME =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate scope;
  private final TransactionTemplate savepoint;
  private final String insertSql;
  private final String deleteSql;
  private final Clock clock;

  public JdbcIdempotencyStore(
      DataSource dataSource, PlatformTransactionManager transactionManager, String tableName) {
    this(dataSource, transactionManager, tableName, Clock.systemUTC());
  }

  public JdbcIdempotencyStore(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      String tableName,
      Clock clock) {
    String table = validTableName(tableName);
    this.jdbc = new JdbcTemplate(dataSource);
    this.scope = new TransactionTemplate(transactionManager);
    this.savepoint = new TransactionTemplate(transactionManager);
    this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    this.insertSql =
        "INSERT INTO " + table + " (handler_id, message_id, processed_at) VALUES (?, ?, ?)";
    this.deleteSql = "DELETE FROM " + table + " WHERE processed_at < ?";
    this.clock = clock;
  }

  /** Returns {@code tableName} when it is a plain or schema-qualified SQL identifier. */
  public static String validTableName(String tableName) {
    if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
      throw new IllegalArgumentException("Invalid table name: " + tableName);
    }
    return tableName;
  }

  @Override
  public <T> T runInScope(Supplier<T> work) {
    return scope.execute(status -> work.get());
  }

  @Override
  public Acquisition tryAcquire(String handlerId, String messageId) {
    try {
      savepoint.executeWithoutResult(
          status -> jdbc.update(insertSql, handlerId, messageId, Timestamp.from(clock.instant())));
      return Acquisition.ACQUIRED;
    } catch (DuplicateKeyException e) {
      return Acquisition.DUPLICATE;
    }
  }

  /** Nothing to do: the marker is committed with the transaction of {@link #runInScope}. */
  @Override
  public void complete(String handlerId, String messageId) {}

  /** Nothing to do: the rollback of {@link #runInScope}'s transaction removes the marker. */
  @Override
  public void release(String handlerId, String messageId) {}

  /** Deletes the markers of messages processed before {@code cutoff}; returns how many. */
  public int deleteProcessedBefore(Instant cutoff) {
    return jdbc.update(deleteSql, Timestamp.from(cutoff));
  }
}
