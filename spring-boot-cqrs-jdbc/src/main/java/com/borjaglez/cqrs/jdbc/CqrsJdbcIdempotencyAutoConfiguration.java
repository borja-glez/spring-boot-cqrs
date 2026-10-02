package com.borjaglez.cqrs.jdbc;

import java.time.Clock;
import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.borjaglez.cqrs.idempotency.IdempotencyStore;

/**
 * Contributes the {@link JdbcIdempotencyStore} when the application has a single {@link DataSource}
 * and transaction manager, unless {@code cqrs.idempotency.store=in-memory} or the application
 * defines its own {@link IdempotencyStore}. Runs before the starters' {@code
 * CqrsIdempotencyAutoConfiguration} so it sees the store.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
      "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
      "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
    },
    beforeName = "com.borjaglez.cqrs.autoconfigure.CqrsIdempotencyAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnSingleCandidate(DataSource.class)
@ConditionalOnBean(PlatformTransactionManager.class)
@ConditionalOnProperty(
    prefix = "cqrs.idempotency",
    name = "store",
    havingValue = "jdbc",
    matchIfMissing = true)
@EnableConfigurationProperties(JdbcCqrsProperties.class)
public class CqrsJdbcIdempotencyAutoConfiguration {

  static final Duration DEFAULT_RETENTION = Duration.ofDays(7);

  @Bean
  @ConditionalOnMissingBean
  public JdbcIdempotencySchemaInitializer jdbcIdempotencySchemaInitializer(
      DataSource dataSource, JdbcCqrsProperties properties) {
    return new JdbcIdempotencySchemaInitializer(
        dataSource, properties.getInitializeSchema(), properties.getIdempotency().getTableName());
  }

  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @DependsOn("jdbcIdempotencySchemaInitializer")
  public JdbcIdempotencyStore jdbcIdempotencyStore(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      JdbcCqrsProperties properties) {
    return new JdbcIdempotencyStore(
        dataSource, transactionManager, properties.getIdempotency().getTableName());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcIdempotencyStore.class)
  @ConditionalOnBooleanProperty(
      name = "cqrs.jdbc.idempotency.cleanup-enabled",
      matchIfMissing = true)
  public JdbcIdempotencyCleanup jdbcIdempotencyCleanup(
      JdbcIdempotencyStore store, JdbcCqrsProperties properties, Environment environment) {
    Duration retention =
        Binder.get(environment)
            .bind("cqrs.idempotency.retention", Duration.class)
            .orElse(DEFAULT_RETENTION);
    return new JdbcIdempotencyCleanup(
        store, retention, properties.getIdempotency().getCleanupInterval(), Clock.systemUTC());
  }
}
