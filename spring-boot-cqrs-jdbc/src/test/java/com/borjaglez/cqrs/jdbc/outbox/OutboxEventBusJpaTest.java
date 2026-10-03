package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.fixtures.TestOrderPlaced;
import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Under a JPA transaction manager the row joins the JPA transaction's connection. */
class OutboxEventBusJpaTest {

  private EmbeddedDatabase dataSource;
  private LocalContainerEntityManagerFactoryBean factory;
  private TransactionTemplate tx;
  private JdbcTemplate jdbc;
  private OutboxEventBus bus;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, OutboxStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();
    factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("com.borjaglez.cqrs.jdbc.none");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.afterPropertiesSet();
    JpaTransactionManager transactionManager = new JpaTransactionManager(factory.getObject());
    transactionManager.setDataSource(dataSource);
    tx = new TransactionTemplate(transactionManager);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE effect (id INT)");
    JacksonMessageSerializer serializer =
        new JacksonMessageSerializer(new ObjectMapper().registerModule(new JavaTimeModule()));
    bus =
        new OutboxEventBus(
            new OutboxStore(dataSource, OutboxStore.DEFAULT_TABLE_NAME),
            serializer,
            new DefaultMessageNamingStrategy(""),
            new OutboxContextCodec(serializer, OutboxTracing.noop()));
  }

  @AfterEach
  void tearDown() {
    factory.destroy();
    dataSource.shutdown();
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  @Test
  void rowCommitsWithTheJpaTransaction() {
    tx.executeWithoutResult(
        status -> {
          jdbc.update("INSERT INTO effect VALUES (1)");
          bus.publish(new TestOrderPlaced("o-1"));
        });

    assertThat(count("effect")).isOne();
    assertThat(count("cqrs_outbox")).isOne();
  }

  @Test
  void rowRollsBackWithTheJpaTransaction() {
    tx.executeWithoutResult(
        status -> {
          jdbc.update("INSERT INTO effect VALUES (1)");
          bus.publish(new TestOrderPlaced("o-1"));
          status.setRollbackOnly();
        });

    assertThat(count("effect")).isZero();
    assertThat(count("cqrs_outbox")).isZero();
  }
}
