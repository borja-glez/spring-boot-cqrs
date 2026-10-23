package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
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
import org.springframework.transaction.TransactionDefinition;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.retry.BackoffStrategy;
import com.borjaglez.cqrs.retry.RetryMiddleware;
import com.borjaglez.cqrs.retry.RetryPolicy;
import com.borjaglez.cqrs.transaction.QueryTransactions;
import com.borjaglez.cqrs.transaction.TransactionMiddleware;

class TransactionMiddlewareJdbcTest {

  private EmbeddedDatabase dataSource;
  private JdbcTemplate jdbc;
  private CountingTransactionManager transactionManager;
  private PlainHandler handler;

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
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE effect (id INT)");
    transactionManager = new CountingTransactionManager(dataSource);
    handler = new PlainHandler(dataSource);
  }

  private SpringCommandBus bus(String handlerId, BusMiddleware... middlewares) throws Exception {
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
        handlerId);
    return new SpringCommandBus(registry, List.of(middlewares));
  }

  private TransactionMiddleware transactions() {
    return new TransactionMiddleware(transactionManager, QueryTransactions.READ_ONLY);
  }

  @AfterEach
  void tearDown() {
    dataSource.shutdown();
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  @Test
  void idempotencyMarkerCommitsWithTheHandlerTransaction() throws Exception {
    SpringCommandBus bus = bus("plain-handler", transactions());
    handler.failuresLeft = 1;

    assertThatThrownBy(() -> bus.dispatch(new PlainCommand())).hasMessageContaining("boom");

    assertThat(count("effect")).isZero();
    assertThat(count("cqrs_processed_message")).isZero();

    bus.dispatch(new PlainCommand());

    assertThat(count("effect")).isOne();
    assertThat(count("cqrs_processed_message")).isOne();
  }

  @Test
  void aFailingHandlerWithoutIdempotencyRollsBackItsWorkOnlyWithTheMiddleware() throws Exception {
    handler.failuresLeft = 1;
    assertThatThrownBy(() -> bus(null, transactions()).dispatch(new PlainCommand()))
        .hasMessageContaining("boom");
    assertThat(count("effect")).isZero();

    handler.failuresLeft = 1;
    assertThatThrownBy(() -> bus(null).dispatch(new PlainCommand())).hasMessageContaining("boom");
    assertThat(count("effect")).isOne();
  }

  @Test
  void theStoreJoinsTheMiddlewareTransactionInsteadOfOpeningItsOwn() throws Exception {
    bus("plain-handler", transactions()).dispatch(new PlainCommand());

    assertThat(transactionManager.begun).isEqualTo(1);
    assertThat(count("cqrs_processed_message")).isOne();
  }

  @Test
  void eachRetryAttemptOfAnIdempotentHandlerRunsInOneTransaction() throws Exception {
    RetryMiddleware retry =
        RetryMiddleware.builder()
            .defaultPolicy(
                RetryPolicy.builder()
                    .maxAttempts(2)
                    .backoff(BackoffStrategy.fixed(Duration.ZERO))
                    .build())
            .build();
    SpringCommandBus bus = bus("plain-handler", retry, transactions());
    handler.failuresLeft = 1;

    bus.dispatch(new PlainCommand());

    assertThat(transactionManager.begun).isEqualTo(2);
    assertThat(count("effect")).isOne();
    assertThat(count("cqrs_processed_message")).isOne();
  }

  /** Counts the physical transactions that are begun. */
  private static class CountingTransactionManager extends DataSourceTransactionManager {
    private static final long serialVersionUID = 1L;

    int begun;

    CountingTransactionManager(DataSource dataSource) {
      super(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun++;
      super.doBegin(transaction, definition);
    }
  }

  static class PlainCommand extends Command {}

  /** A handler with no Spring annotations, as in a hexagonal application. */
  public static class PlainHandler {
    private final JdbcTemplate jdbc;
    int failuresLeft;

    PlainHandler(DataSource dataSource) {
      this.jdbc = new JdbcTemplate(dataSource);
    }

    public void handle(PlainCommand command) {
      jdbc.update("INSERT INTO effect VALUES (1)");
      if (failuresLeft-- > 0) {
        throw new IllegalStateException("boom");
      }
    }
  }
}
