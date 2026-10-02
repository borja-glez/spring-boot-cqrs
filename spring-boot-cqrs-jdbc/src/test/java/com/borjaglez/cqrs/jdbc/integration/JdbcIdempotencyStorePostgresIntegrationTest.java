package com.borjaglez.cqrs.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.jdbc.JdbcIdempotencyStore;

@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class JdbcIdempotencyStorePostgresIntegrationTest {

  private static PostgreSQLContainer<?> postgres;
  private static DriverManagerDataSource dataSource;
  private static DataSourceTransactionManager transactionManager;
  private static JdbcTemplate jdbc;

  static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @BeforeAll
  static void start() {
    postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    postgres.start();
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    new ResourceDatabasePopulator(
            new ClassPathResource("com/borjaglez/cqrs/jdbc/schema-idempotency.sql"))
        .execute(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void stop() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM cqrs_processed_message");
  }

  private IdempotentInvoker invoker() {
    return new IdempotentInvoker(
        new JdbcIdempotencyStore(
            dataSource, transactionManager, JdbcIdempotencyStore.DEFAULT_TABLE_NAME));
  }

  @Test
  void concurrentDeliveriesApplyTheEffectOnce() throws Exception {
    IdempotentInvoker invoker = invoker();
    AtomicInteger applied = new AtomicInteger();
    int threads = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return invoker.invoke(
                      "h",
                      "m",
                      () -> {
                        applied.incrementAndGet();
                        sleep(200); // keep the row uncommitted while the others insert
                        return null;
                      });
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(applied).hasValue(1);
  }

  @Test
  void aConcurrentDeliveryRunsWhenTheFirstRollsBack() throws Exception {
    IdempotentInvoker invoker = invoker();
    AtomicInteger applied = new AtomicInteger();
    CountDownLatch firstInserted = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(
              () ->
                  invoker.invoke(
                      "h",
                      "m",
                      () -> {
                        firstInserted.countDown();
                        sleep(300);
                        throw new IllegalStateException("first fails");
                      }));
      assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();
      Future<?> second = executor.submit(() -> invoker.invoke("h", "m", applied::incrementAndGet));
      assertThat(first)
          .failsWithin(30, TimeUnit.SECONDS)
          .withThrowableThat()
          .withMessageContaining("first fails");
      second.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(applied).hasValue(1);
  }

  @Test
  void duplicateInsideAnOuterTransactionKeepsItUsable() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS effect (id INT)");
    jdbc.update("DELETE FROM effect");
    IdempotentInvoker invoker = invoker();
    invoker.invoke("h", "m", () -> null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(invoker.invoke("h", "m", () -> "x").duplicate()).isTrue();
              jdbc.update("INSERT INTO effect VALUES (1)");
            });

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isOne();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
