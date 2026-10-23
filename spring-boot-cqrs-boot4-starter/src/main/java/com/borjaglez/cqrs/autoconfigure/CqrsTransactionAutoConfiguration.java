package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import com.borjaglez.cqrs.transaction.TransactionMiddleware;

/**
 * Registers the {@link TransactionMiddleware}, which runs handlers in a transaction. Opt-in: only
 * when {@code cqrs.transactions.enabled=true}, the application has a single {@link
 * PlatformTransactionManager} (or a primary one) and defines no {@link TransactionMiddleware} of
 * its own.
 */
@AutoConfiguration(
    afterName = {
      "com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration",
      "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
      "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
    })
@ConditionalOnProperty(prefix = "cqrs.transactions", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CqrsProperties.class)
public class CqrsTransactionAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnSingleCandidate(PlatformTransactionManager.class)
  public TransactionMiddleware transactionMiddleware(
      PlatformTransactionManager transactionManager, CqrsProperties properties) {
    return new TransactionMiddleware(transactionManager, properties.getTransactions().getQueries());
  }
}
