package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;

class OutboxStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

  private EmbeddedDatabase dataSource;
  private JdbcTemplate jdbc;
  private TransactionTemplate tx;
  private OutboxStore store;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, OutboxStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();
    jdbc = new JdbcTemplate(dataSource);
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    store =
        new OutboxStore(
            dataSource, OutboxStore.DEFAULT_TABLE_NAME, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    dataSource.shutdown();
  }

  private void insert(String eventId) {
    store.insert(eventId, "order-placed", "com.example.OrderPlaced", new byte[] {1, 2}, null);
  }

  private List<OutboxRecord> lock(int batchSize) {
    return tx.execute(status -> store.lockBatch(batchSize));
  }

  private Timestamp column(String column, String eventId) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM cqrs_outbox WHERE event_id = ?", Timestamp.class, eventId);
  }

  @Test
  void insertedRowsAreLockedInInsertionOrder() {
    store.insert("e-1", "order-placed", "com.example.OrderPlaced", new byte[] {1}, new byte[] {9});
    insert("e-2");

    List<OutboxRecord> rows = lock(10);

    assertThat(rows).extracting(OutboxRecord::eventId).containsExactly("e-1", "e-2");
    OutboxRecord first = rows.get(0);
    assertThat(first.eventName()).isEqualTo("order-placed");
    assertThat(first.eventClass()).isEqualTo("com.example.OrderPlaced");
    assertThat(first.payload()).containsExactly(1);
    assertThat(first.context()).containsExactly(9);
    assertThat(first.attempts()).isZero();
    assertThat(rows.get(1).context()).isNull();
    assertThat(column("created_at", "e-1")).isEqualTo(Timestamp.from(NOW));
  }

  @Test
  void lockBatchReturnsAtMostTheBatchSize() {
    insert("e-1");
    insert("e-2");
    insert("e-3");

    assertThat(lock(2)).extracting(OutboxRecord::eventId).containsExactly("e-1", "e-2");
  }

  @Test
  void rowsLockedByAnotherTransactionAreSkipped() throws Exception {
    insert("e-1");
    insert("e-2");
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService other = Executors.newSingleThreadExecutor();
    try {
      Future<List<OutboxRecord>> first =
          other.submit(
              () ->
                  tx.execute(
                      status -> {
                        List<OutboxRecord> rows = store.lockBatch(1);
                        locked.countDown();
                        awaitQuietly(release);
                        return rows;
                      }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

      List<OutboxRecord> second = lock(10);
      release.countDown();

      assertThat(first.get(5, TimeUnit.SECONDS))
          .extracting(OutboxRecord::eventId)
          .containsExactly("e-1");
      assertThat(second).extracting(OutboxRecord::eventId).containsExactly("e-2");
    } finally {
      release.countDown();
      other.shutdownNow();
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void publishedRowsAreNoLongerPending() {
    insert("e-1");
    insert("e-2");
    long id = lock(1).get(0).id();

    store.markPublished(id);

    assertThat(column("published_at", "e-1")).isEqualTo(Timestamp.from(NOW));
    assertThat(lock(10)).extracting(OutboxRecord::eventId).containsExactly("e-2");
  }

  @Test
  void failedAttemptKeepsTheRowPendingWithItsError() {
    insert("e-1");
    long id = lock(1).get(0).id();

    store.markFailed(id, 3, "java.lang.IllegalStateException: broker down", false);

    OutboxRecord row = lock(1).get(0);
    assertThat(row.attempts()).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_error FROM cqrs_outbox WHERE id = ?", String.class, id))
        .isEqualTo("java.lang.IllegalStateException: broker down");
    assertThat(column("failed_at", "e-1")).isNull();
  }

  @Test
  void setAsideRowIsNoLongerPending() {
    insert("e-1");
    long id = lock(1).get(0).id();

    store.markFailed(id, 10, "poison", true);

    assertThat(lock(10)).isEmpty();
    assertThat(column("failed_at", "e-1")).isEqualTo(Timestamp.from(NOW));
  }

  @Test
  void longErrorsAreTruncatedAndNullErrorsKept() {
    insert("e-1");
    insert("e-2");
    List<OutboxRecord> rows = lock(2);

    store.markFailed(rows.get(0).id(), 1, "x".repeat(5000), false);
    store.markFailed(rows.get(1).id(), 1, null, false);

    assertThat(
            jdbc.queryForObject(
                "SELECT last_error FROM cqrs_outbox WHERE event_id = 'e-1'", String.class))
        .hasSize(OutboxStore.MAX_ERROR_LENGTH);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_error FROM cqrs_outbox WHERE event_id = 'e-2'", String.class))
        .isNull();
  }

  @Test
  void deletePublishedBeforeKeepsPendingAndSetAsideRows() {
    insert("published");
    insert("pending");
    insert("set-aside");
    List<OutboxRecord> rows = lock(3);
    store.markPublished(rows.get(0).id());
    store.markFailed(rows.get(2).id(), 10, "poison", true);

    assertThat(store.deletePublishedBefore(NOW)).isZero();
    assertThat(store.deletePublishedBefore(NOW.plus(Duration.ofSeconds(1)))).isOne();

    assertThat(jdbc.queryForList("SELECT event_id FROM cqrs_outbox ORDER BY id", String.class))
        .containsExactly("pending", "set-aside");
  }

  @Test
  void defaultClockConstructorWorks() {
    new OutboxStore(dataSource, OutboxStore.DEFAULT_TABLE_NAME)
        .insert("e-1", "n", "c", new byte[] {1}, null);

    assertThat(column("created_at", "e-1")).isNotNull();
  }

  @Test
  void rejectsAnInvalidTableName() {
    assertThatThrownBy(() -> new OutboxStore(dataSource, "bad name"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void usesTheSubqueryFormOnlyOnH2() {
    assertThat(OutboxStore.lockSql("H2", "t"))
        .contains("WHERE id IN (SELECT id FROM t")
        .endsWith("FOR UPDATE SKIP LOCKED");
    assertThat(OutboxStore.lockSql("PostgreSQL", "t"))
        .doesNotContain("IN (SELECT")
        .endsWith("ORDER BY id FETCH FIRST %d ROWS ONLY FOR UPDATE SKIP LOCKED");
  }

  @Test
  void lockBatchReportsAnUndetectableDatabase() throws SQLException {
    DataSource broken = mock(DataSource.class);
    when(broken.getConnection()).thenThrow(new SQLException("down"));

    assertThatThrownBy(() -> new OutboxStore(broken, "t").lockBatch(1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("detect");
  }
}
