package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionDefinition;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.event.spring.SpringEventBus;
import com.borjaglez.cqrs.event.transactional.TransactionalEventBus;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.retry.BackoffStrategy;
import com.borjaglez.cqrs.retry.RetryMiddleware;
import com.borjaglez.cqrs.retry.RetryPolicy;
import com.borjaglez.cqrs.transaction.QueryTransactions;
import com.borjaglez.cqrs.transaction.TransactionMiddleware;

class TransactionMiddlewareJdbcTest {

  private static final int COMMAND_ROW = 1;
  private static final int EVENT_ROW = 2;

  private EmbeddedDatabase dataSource;
  private JdbcTemplate jdbc;
  private CountingTransactionManager transactionManager;
  private PlainHandler handler;
  private PlainEventHandler eventHandler;

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

  /** Committed rows written by a handler, read on a connection of their own. */
  private int rows(int id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM effect WHERE id = ?", Integer.class, id);
  }

  /** Committed idempotency markers of a handler, read on a connection of their own. */
  private int markers(String handlerId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM cqrs_processed_message WHERE handler_id = ?",
        Integer.class,
        handlerId);
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

  @ParameterizedTest(name = "{displayName} [middleware {0}, auto-commit {1}]")
  @CsvSource({"true, true", "true, false", "false, true", "false, false"})
  void anEventDeliveredAfterCommitRunsInItsOwnTransaction(boolean middleware, boolean autoCommit)
      throws Exception {
    SpringCommandBus bus = publishingBus(middleware, autoCommit);

    bus.dispatch(new PlainCommand());

    assertThat(rows(COMMAND_ROW)).isOne();
    assertThat(markers("command-handler")).isOne();
    assertThat(rows(EVENT_ROW)).isOne();
    assertThat(markers("event-handler")).isOne();
    assertThat(transactionManager.begun).isEqualTo(2);
  }

  @ParameterizedTest(name = "{displayName} [middleware {0}, auto-commit {1}]")
  @CsvSource({"true, true", "true, false", "false, true", "false, false"})
  void aFailingAfterCommitEventHandlerDoesNotAffectTheCommittedCommand(
      boolean middleware, boolean autoCommit) throws Exception {
    SpringCommandBus bus = publishingBus(middleware, autoCommit);
    eventHandler.fail = true;

    // The event is delivered when the command's transaction commits, so its failure still
    // reaches the caller of the command.
    assertThatThrownBy(() -> bus.dispatch(new PlainCommand())).hasMessage("event boom");

    assertThat(rows(COMMAND_ROW)).isOne();
    assertThat(markers("command-handler")).isOne();
    assertThat(rows(EVENT_ROW)).isZero();
    assertThat(markers("event-handler")).isZero();
    assertThat(transactionManager.begun).isEqualTo(2);
  }

  /**
   * A command handler that writes a row and publishes an event on the default event bus, which
   * delivers it after the command's transaction commits; the event handler writes another row. Both
   * handlers are idempotent and carry no Spring annotations; the transactions come from the
   * middleware or, without it, from the idempotency store.
   *
   * @param middleware whether the {@link TransactionMiddleware} is in the chain; without it the
   *     idempotency store is the only source of transactions
   * @param autoCommit whether the data source hands out connections in auto-commit mode (as a pool
   *     does by default) or not (as a pool configured with {@code auto-commit=false} does)
   */
  private SpringCommandBus publishingBus(boolean middleware, boolean autoCommit) throws Exception {
    DataSource connections = autoCommit ? dataSource : withoutAutoCommit(dataSource);
    transactionManager = new CountingTransactionManager(connections);
    IdempotentInvoker idempotency =
        new IdempotentInvoker(
            new JdbcIdempotencyStore(
                connections, transactionManager, JdbcIdempotencyStore.DEFAULT_TABLE_NAME));
    List<BusMiddleware> middlewares =
        middleware
            ? List.of(new TransactionMiddleware(transactionManager, QueryTransactions.READ_ONLY))
            : List.of();

    EventHandlerRegistry eventHandlers = new EventHandlerRegistry();
    eventHandlers.setIdempotentInvoker(idempotency);
    eventHandler = new PlainEventHandler(connections);
    eventHandlers.register(
        PlainEvent.class,
        eventHandler,
        PlainEventHandler.class.getMethod("on", PlainEvent.class),
        "plain-event",
        null,
        null,
        true,
        "event-handler");
    EventBus events = new TransactionalEventBus(new SpringEventBus(eventHandlers, middlewares));

    CommandHandlerRegistry commandHandlers = new CommandHandlerRegistry();
    commandHandlers.setIdempotentInvoker(idempotency);
    commandHandlers.register(
        PlainCommand.class,
        new PublishingHandler(connections, events),
        PublishingHandler.class.getMethod("handle", PlainCommand.class),
        "plain",
        false,
        true,
        "command-handler");
    return new SpringCommandBus(commandHandlers, middlewares);
  }

  private static DataSource withoutAutoCommit(DataSource dataSource) {
    return new DelegatingDataSource(dataSource) {
      @Override
      public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        connection.setAutoCommit(false);
        return connection;
      }
    };
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

  static class PlainEvent extends Event {}

  /** Writes the command's row and publishes an event. */
  public static class PublishingHandler {
    private final JdbcTemplate jdbc;
    private final EventBus events;

    PublishingHandler(DataSource dataSource, EventBus events) {
      this.jdbc = new JdbcTemplate(dataSource);
      this.events = events;
    }

    public void handle(PlainCommand command) {
      jdbc.update("INSERT INTO effect VALUES (?)", COMMAND_ROW);
      events.publish(new PlainEvent());
    }
  }

  /** Writes the event's row, then fails when told to. */
  public static class PlainEventHandler {
    private final JdbcTemplate jdbc;
    boolean fail;

    PlainEventHandler(DataSource dataSource) {
      this.jdbc = new JdbcTemplate(dataSource);
    }

    public void on(PlainEvent event) {
      jdbc.update("INSERT INTO effect VALUES (?)", EVENT_ROW);
      if (fail) {
        throw new IllegalStateException("event boom");
      }
    }
  }
}
