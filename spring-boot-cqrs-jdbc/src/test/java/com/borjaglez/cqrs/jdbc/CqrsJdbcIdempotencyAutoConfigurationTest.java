package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsIdempotencyAutoConfiguration;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

class CqrsJdbcIdempotencyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues("spring.datasource.generate-unique-name=true")
          .withConfiguration(
              AutoConfigurations.of(
                  DataSourceAutoConfiguration.class,
                  DataSourceTransactionManagerAutoConfiguration.class,
                  JdbcTemplateAutoConfiguration.class,
                  CqrsAutoConfiguration.class,
                  CqrsIdempotencyAutoConfiguration.class,
                  CqrsJdbcIdempotencyAutoConfiguration.class));

  @Test
  void registersTheJdbcStoreTheInvokerTheSchemaAndTheCleanup() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(JdbcIdempotencyStore.class);
          assertThat(context).hasSingleBean(IdempotentInvoker.class);
          assertThat(context).hasSingleBean(JdbcIdempotencyCleanup.class);
          assertThat(
                  context
                      .getBean(JdbcTemplate.class)
                      .queryForObject("SELECT COUNT(*) FROM cqrs_processed_message", Integer.class))
              .isZero();
          assertThat(context.getBean(JdbcIdempotencyCleanup.class).retention())
              .isEqualTo(Duration.ofDays(7));
        });
  }

  @Test
  void inMemoryStoreSelectedByPropertyWins() {
    contextRunner
        .withPropertyValues("cqrs.idempotency.store=in-memory")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JdbcIdempotencyStore.class);
              assertThat(context).hasSingleBean(InMemoryIdempotencyStore.class);
              assertThat(context).doesNotHaveBean(JdbcIdempotencyCleanup.class);
            });
  }

  @Test
  void cleanupCanBeDisabled() {
    contextRunner
        .withPropertyValues("cqrs.jdbc.idempotency.cleanup-enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(JdbcIdempotencyCleanup.class));
  }

  @Test
  void bindsTheJdbcProperties() {
    contextRunner
        .withPropertyValues(
            "cqrs.jdbc.initialize-schema=always",
            "cqrs.jdbc.idempotency.table-name=markers",
            "cqrs.jdbc.idempotency.cleanup-interval=10m",
            "cqrs.idempotency.retention=1d")
        .run(
            context -> {
              JdbcCqrsProperties properties = context.getBean(JdbcCqrsProperties.class);
              assertThat(properties.getInitializeSchema())
                  .isEqualTo(JdbcCqrsProperties.InitializeSchema.ALWAYS);
              assertThat(properties.getIdempotency().getTableName()).isEqualTo("markers");
              assertThat(properties.getIdempotency().getCleanupInterval())
                  .isEqualTo(Duration.ofMinutes(10));
              assertThat(
                      context
                          .getBean(JdbcTemplate.class)
                          .queryForObject("SELECT COUNT(*) FROM markers", Integer.class))
                  .isZero();
              assertThat(context.getBean(JdbcIdempotencyCleanup.class).retention())
                  .isEqualTo(Duration.ofDays(1));
            });
  }

  @Test
  void userStoreDisablesTheJdbcStore() {
    contextRunner
        .withBean(
            IdempotencyStore.class,
            () -> new InMemoryIdempotencyStore(Duration.ofDays(1), Duration.ofMinutes(1)))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JdbcIdempotencyStore.class);
              assertThat(context).doesNotHaveBean(JdbcIdempotencyCleanup.class);
              assertThat(context).hasSingleBean(IdempotentInvoker.class);
            });
  }

  @Test
  void backsOffWithoutADataSource() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CqrsAutoConfiguration.class,
                CqrsIdempotencyAutoConfiguration.class,
                CqrsJdbcIdempotencyAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(JdbcIdempotencyStore.class));
  }

  @Test
  void jdbcPropertyDefaults() {
    JdbcCqrsProperties properties = new JdbcCqrsProperties();

    assertThat(properties.getInitializeSchema())
        .isEqualTo(JdbcCqrsProperties.InitializeSchema.EMBEDDED);
    assertThat(properties.getIdempotency().getTableName())
        .isEqualTo(JdbcIdempotencyStore.DEFAULT_TABLE_NAME);
    assertThat(properties.getIdempotency().isCleanupEnabled()).isTrue();
    assertThat(properties.getIdempotency().getCleanupInterval()).isEqualTo(Duration.ofHours(1));
  }
}
