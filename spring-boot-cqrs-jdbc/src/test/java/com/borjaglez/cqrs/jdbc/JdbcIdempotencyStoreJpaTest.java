package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

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

  @Test
  void duplicateUnderJpaTransactionManagerKeepsTheOuterTransactionUsable() {
    EmbeddedDatabase dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new ResourceDatabasePopulator(
            new ClassPathResource("com/borjaglez/cqrs/jdbc/schema-idempotency.sql"))
        .execute(dataSource);
    LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("com.borjaglez.cqrs.jdbc.none");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.afterPropertiesSet();
    EntityManagerFactory emf = factory.getObject();
    JpaTransactionManager transactionManager = new JpaTransactionManager(emf);
    transactionManager.setDataSource(dataSource);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE effect (id INT)");
    IdempotentInvoker invoker =
        new IdempotentInvoker(
            new JdbcIdempotencyStore(
                dataSource, transactionManager, JdbcIdempotencyStore.DEFAULT_TABLE_NAME));
    invoker.invoke("h", "m", () -> null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(invoker.invoke("h", "m", () -> "x").duplicate()).isTrue();
              jdbc.update("INSERT INTO effect VALUES (1)");
            });

    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM effect", Integer.class)).isOne();
    factory.destroy();
    dataSource.shutdown();
  }
}
