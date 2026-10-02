package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.idempotency.IdempotentInvoker;

class JdbcIdempotencyStoreJpaTest {

  private EmbeddedDatabase dataSource;
  private LocalContainerEntityManagerFactoryBean factory;
  private JpaTransactionManager transactionManager;
  private JdbcTemplate jdbc;
  private IdempotentInvoker invoker;

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
    factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("com.borjaglez.cqrs.jdbc.none");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.afterPropertiesSet();
    EntityManagerFactory emf = factory.getObject();
    transactionManager = new JpaTransactionManager(emf);
    transactionManager.setDataSource(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE effect (id INT)");
    invoker =
        new IdempotentInvoker(
            new JdbcIdempotencyStore(
                dataSource, transactionManager, JdbcIdempotencyStore.DEFAULT_TABLE_NAME));
  }

  @AfterEach
  void tearDown() {
    factory.destroy();
    dataSource.shutdown();
  }

  private int markers(String messageId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM cqrs_processed_message WHERE message_id = ?",
        Integer.class,
        messageId);
  }

  private int effects(int id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM effect WHERE id = ?", Integer.class, id);
  }

  @Test
  void duplicateUnderJpaTransactionManagerKeepsTheOuterTransactionUsable() {
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
  void failedEffectRollsBackTheMarkerAndTheWorkUnderJpaTransactionManager() {
    assertThatThrownBy(
            () ->
                invoker.invoke(
                    "h",
                    "m2",
                    () -> {
                      jdbc.update("INSERT INTO effect VALUES (2)");
                      throw new IllegalStateException("boom");
                    }))
        .hasMessage("boom");

    assertThat(markers("m2")).isZero();
    assertThat(effects(2)).isZero();
    assertThat(invoker.invoke("h", "m2", () -> "retried").duplicate()).isFalse();
    assertThat(markers("m2")).isOne();
  }

  @Test
  void rollbackOnlyOuterTransactionRemovesTheMarker() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(invoker.invoke("h", "m3", () -> "ok").duplicate()).isFalse();
              status.setRollbackOnly();
            });

    assertThat(markers("m3")).isZero();
  }
}
