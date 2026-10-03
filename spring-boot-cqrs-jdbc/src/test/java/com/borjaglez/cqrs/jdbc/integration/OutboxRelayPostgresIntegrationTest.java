package com.borjaglez.cqrs.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.TestOrderPlaced;
import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;
import com.borjaglez.cqrs.jdbc.outbox.OutboxContextCodec;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventBus;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventTypeResolver;
import com.borjaglez.cqrs.jdbc.outbox.OutboxRelay;
import com.borjaglez.cqrs.jdbc.outbox.OutboxSchemaInitializer;
import com.borjaglez.cqrs.jdbc.outbox.OutboxStore;
import com.borjaglez.cqrs.jdbc.outbox.OutboxTracing;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class OutboxRelayPostgresIntegrationTest {

  private static PostgreSQLContainer<?> postgres;
  private static DriverManagerDataSource dataSource;
  private static DataSourceTransactionManager transactionManager;
  private static JdbcTemplate jdbc;

  private final JacksonMessageSerializer serializer =
      new JacksonMessageSerializer(new ObjectMapper().registerModule(new JavaTimeModule()));
  private final DefaultMessageNamingStrategy naming = new DefaultMessageNamingStrategy("");
  private final OutboxContextCodec codec = new OutboxContextCodec(serializer, OutboxTracing.noop());

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
    new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, OutboxStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();
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
    jdbc.update("DELETE FROM cqrs_outbox");
  }

  private OutboxRelay relay(OutboxStore store, EventBus target) {
    return new OutboxRelay(
        store,
        new OutboxEventTypeResolver(
            new EventHandlerRegistry(), naming, Set.of(), getClass().getClassLoader()),
        serializer,
        codec,
        target,
        transactionManager,
        10,
        3);
  }

  @Test
  void twoRelaysPublishEachRowExactlyOnce() throws Exception {
    OutboxStore store = new OutboxStore(dataSource, OutboxStore.DEFAULT_TABLE_NAME);
    OutboxEventBus outbox = new OutboxEventBus(store, serializer, naming, codec);
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    for (int i = 0; i < 200; i++) {
      String orderId = "o-" + i;
      tx.executeWithoutResult(s -> outbox.publish(new TestOrderPlaced(orderId)));
    }
    Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
    EventBus counting =
        new EventBus() {
          @Override
          public void publish(Event event) {
            try {
              Thread.sleep(1);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            deliveries
                .computeIfAbsent(event.getEventId(), id -> new AtomicInteger())
                .incrementAndGet();
          }

          @Override
          public void publish(List<Event> events) {
            events.forEach(this::publish);
          }
        };
    OutboxRelay first = relay(store, counting);
    OutboxRelay second = relay(store, counting);
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    int publishedByFirst;
    int publishedBySecond;
    try {
      Future<Integer> a = pool.submit(() -> drain(first, start));
      Future<Integer> b = pool.submit(() -> drain(second, start));
      publishedByFirst = a.get(60, TimeUnit.SECONDS);
      publishedBySecond = b.get(60, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    drain(first); // rows released by the other relay at the very end

    // Both relays took part: the rows were shared, not drained by one of them alone.
    assertThat(publishedByFirst).isPositive();
    assertThat(publishedBySecond).isPositive();
    assertThat(deliveries).hasSize(200);
    assertThat(deliveries.values()).allSatisfy(count -> assertThat(count).hasValue(1));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM cqrs_outbox WHERE published_at IS NULL", Integer.class))
        .isZero();
  }

  /** Relays until nothing is left for this relay; returns how many rows it published. */
  private static int drain(OutboxRelay relay) {
    int published = 0;
    OutboxRelay.BatchResult result;
    do {
      result = relay.relayBatch();
      published += result.published();
    } while (result.locked() > 0);
    return published;
  }

  private static int drain(OutboxRelay relay, CyclicBarrier start) throws Exception {
    start.await(10, TimeUnit.SECONDS);
    return drain(relay);
  }

  @Test
  void schemaQualifiedTableGetsItsIndexesInItsSchema() {
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS outbox_it");

    new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, "outbox_it.events_outbox")
        .afterPropertiesSet();

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'outbox_it'"
                    + " AND indexname IN ('events_outbox_pending', 'events_outbox_published')",
                Integer.class))
        .isEqualTo(2);
  }
}
