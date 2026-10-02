package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;

class JdbcIdempotencyStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  private DataSource dataSource;
  private DataSourceTransactionManager transactionManager;
  private JdbcTemplate jdbc;
  private JdbcIdempotencyStore store;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new ResourceDatabasePopulator(
            new ClassPathResource("com/borjaglez/cqrs/jdbc/schema-idempotency.sql"))
        .execute(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    store =
        new JdbcIdempotencyStore(
            dataSource,
            transactionManager,
            JdbcIdempotencyStore.DEFAULT_TABLE_NAME,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private int markers() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM cqrs_processed_message", Integer.class);
  }

  @Test
  void successfulEffectCommitsItsMarker() {
    IdempotentInvoker invoker = new IdempotentInvoker(store);

    assertThat(invoker.invoke("h", "m", () -> "done").duplicate()).isFalse();
    assertThat(invoker.invoke("h", "m", () -> "again").duplicate()).isTrue();
    assertThat(markers()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT processed_at FROM cqrs_processed_message", java.sql.Timestamp.class))
        .isEqualTo(java.sql.Timestamp.from(NOW));
  }

  @Test
  void failedEffectRollsBackItsMarkerAndItsWork() {
    jdbc.execute("CREATE TABLE effect (id INT)");
    IdempotentInvoker invoker = new IdempotentInvoker(store);

    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m",
                    () -> {
                      jdbc.update("INSERT INTO effect VALUES (1)");
                      throw new IllegalStateException("boom");
                    }))
        .hasMessage("boom");

    assertThat(markers()).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isZero();
    assertThat(invoker.invoke("h", "m", () -> "retried").result()).isEqualTo("retried");
  }

  @Test
  void duplicateInsideAnOuterTransactionKeepsItUsable() {
    jdbc.execute("CREATE TABLE effect (id INT)");
    IdempotentInvoker invoker = new IdempotentInvoker(store);
    invoker.invoke("h", "m", () -> null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(invoker.invoke("h", "m", () -> "x").duplicate()).isTrue();
              jdbc.update("INSERT INTO effect VALUES (1)");
            });

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isOne();
  }

  @Test
  void completeAndReleaseLeaveTheWorkToTheTransaction() {
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
    store.complete("h", "m");
    store.release("h", "m");

    assertThat(markers()).isEqualTo(1);
  }

  @Test
  void deletesMarkersProcessedBeforeACutoff() {
    store.tryAcquire("h", "old");
    JdbcIdempotencyStore later =
        new JdbcIdempotencyStore(
            dataSource,
            transactionManager,
            JdbcIdempotencyStore.DEFAULT_TABLE_NAME,
            Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
    later.tryAcquire("h", "new");

    assertThat(store.deleteProcessedBefore(NOW.plusSeconds(1))).isOne();
    assertThat(jdbc.queryForList("SELECT message_id FROM cqrs_processed_message", String.class))
        .containsExactly("new");
  }

  @Test
  void usesACustomTableName() {
    jdbc.execute(
        "CREATE TABLE markers (handler_id VARCHAR(255) NOT NULL, message_id VARCHAR(64) NOT NULL,"
            + " processed_at TIMESTAMP NOT NULL, PRIMARY KEY (handler_id, message_id))");
    JdbcIdempotencyStore custom =
        new JdbcIdempotencyStore(dataSource, transactionManager, "markers");

    assertThat(custom.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM markers", Integer.class)).isOne();
  }

  @Test
  void rejectsUnsafeTableNames() {
    assertThatThrownBy(() -> JdbcIdempotencyStore.validTableName("t; DROP TABLE x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table name: t; DROP TABLE x");
    assertThatThrownBy(() -> JdbcIdempotencyStore.validTableName(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table name: null");
    assertThat(JdbcIdempotencyStore.validTableName("cqrs.processed_message"))
        .isEqualTo("cqrs.processed_message");
  }

  private void withBoundConnection(Connection connection, Runnable action) throws SQLException {
    connection.setAutoCommit(false);
    TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
    try {
      action.run();
    } finally {
      TransactionSynchronizationManager.unbindResource(dataSource);
      connection.rollback();
      connection.close();
    }
  }

  @Test
  void ignoresAFailureToReleaseTheSavepoint() throws Exception {
    Connection connection = spy(dataSource.getConnection());
    doThrow(new SQLException("no release")).when(connection).releaseSavepoint(any());

    withBoundConnection(
        connection, () -> assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED));

    verify(connection).releaseSavepoint(any());
  }

  @Test
  void rollsBackToTheSavepointWhenTheInsertFailsForAnotherReason() throws Exception {
    JdbcIdempotencyStore broken =
        new JdbcIdempotencyStore(dataSource, transactionManager, "missing_table");
    Connection connection = spy(dataSource.getConnection());

    withBoundConnection(
        connection,
        () ->
            assertThatThrownBy(() -> broken.tryAcquire("h", "m"))
                .isInstanceOf(BadSqlGrammarException.class));

    verify(connection).rollback(any(Savepoint.class));
    verify(connection).releaseSavepoint(any());
  }

  @Test
  void translatesAFailureToCreateTheSavepoint() throws Exception {
    Connection connection = spy(dataSource.getConnection());
    doThrow(new SQLException("no savepoint")).when(connection).setSavepoint(any());

    withBoundConnection(
        connection,
        () ->
            assertThatThrownBy(() -> store.tryAcquire("h", "m"))
                .isInstanceOf(TransactionSystemException.class)
                .hasMessage("Could not create JDBC savepoint"));
  }

  @Test
  void translatesAFailureToRollBackToTheSavepoint() throws Exception {
    store.tryAcquire("h", "m");
    Connection connection = spy(dataSource.getConnection());
    doThrow(new SQLException("no rollback")).when(connection).rollback(any(Savepoint.class));

    withBoundConnection(
        connection,
        () ->
            assertThatThrownBy(() -> store.tryAcquire("h", "m"))
                .isInstanceOf(TransactionSystemException.class)
                .hasMessage("Could not roll back to JDBC savepoint"));
  }
}
