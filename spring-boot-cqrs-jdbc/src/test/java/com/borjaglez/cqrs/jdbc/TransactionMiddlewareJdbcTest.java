package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.transaction.QueryTransactions;
import com.borjaglez.cqrs.transaction.TransactionMiddleware;

class TransactionMiddlewareJdbcTest {

  private EmbeddedDatabase dataSource;
  private JdbcTemplate jdbc;
  private SpringCommandBus bus;
  private PlainHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new ResourceDatabasePopulator(
            new ClassPathResource("com/borjaglez/cqrs/jdbc/schema-idempotency.sql"))
        .execute(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE effect (id INT)");
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    handler = new PlainHandler(dataSource);
    CommandHandlerRegistry registry = new CommandHandlerRegistry();
    registry.setIdempotentInvoker(
        new IdempotentInvoker(
            new JdbcIdempotencyStore(
                dataSource, transactionManager, JdbcIdempotencyStore.DEFAULT_TABLE_NAME)));
    registry.register(
        PlainCommand.class,
        handler,
        PlainHandler.class.getMethod("handle", PlainCommand.class),
        "plain",
        false,
        true,
        "plain-handler");
    bus =
        new SpringCommandBus(
            registry,
            List.of(new TransactionMiddleware(transactionManager, QueryTransactions.READ_ONLY)));
  }

  @AfterEach
  void tearDown() {
    dataSource.shutdown();
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  @Test
  void idempotencyMarkerCommitsWithTheHandlerTransaction() {
    handler.fail = true;

    assertThatThrownBy(() -> bus.dispatch(new PlainCommand())).hasMessageContaining("boom");

    assertThat(count("effect")).isZero();
    assertThat(count("cqrs_processed_message")).isZero();

    handler.fail = false;
    bus.dispatch(new PlainCommand());

    assertThat(count("effect")).isOne();
    assertThat(count("cqrs_processed_message")).isOne();
  }

  static class PlainCommand extends Command {}

  /** A handler with no Spring annotations, as in a hexagonal application. */
  public static class PlainHandler {
    private final JdbcTemplate jdbc;
    boolean fail;

    PlainHandler(DataSource dataSource) {
      this.jdbc = new JdbcTemplate(dataSource);
    }

    public void handle(PlainCommand command) {
      jdbc.update("INSERT INTO effect VALUES (1)");
      if (fail) {
        throw new IllegalStateException("boom");
      }
    }
  }
}
