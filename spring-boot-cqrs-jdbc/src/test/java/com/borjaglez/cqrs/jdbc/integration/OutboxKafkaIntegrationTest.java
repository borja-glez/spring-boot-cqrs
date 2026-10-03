package com.borjaglez.cqrs.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.fixtures.TestOrderPlaced;
import com.borjaglez.cqrs.jdbc.outbox.CqrsJdbcOutboxAutoConfiguration;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventBus;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsAutoConfiguration;
import com.borjaglez.cqrs.kafka.config.KafkaEventBusAutoConfiguration;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;

/** End to end: PostgreSQL outbox, relay through KafkaEventBus, consumed by a local handler. */
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker is not available")
class OutboxKafkaIntegrationTest {

  private static PostgreSQLContainer<?> postgres;
  private static KafkaContainer kafka;
  private static JdbcTemplate jdbc;

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
    kafka = new KafkaContainer("apache/kafka-native:3.8.0");
    postgres.start();
    kafka.start();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  @AfterAll
  static void stop() {
    if (kafka != null) {
      kafka.stop();
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void clean() {
    jdbc.execute("DROP TABLE IF EXISTS cqrs_outbox");
  }

  @EventHandler
  public static class OrderPlacedCollector {

    final BlockingQueue<String> received = new LinkedBlockingQueue<>();

    @HandleEvent
    public void on(TestOrderPlaced event) {
      received.add(event.getOrderId() + "|" + MessageContext.current().correlationId());
    }
  }

  private ApplicationContextRunner runner(String prefix) {
    return new ApplicationContextRunner()
        .withPropertyValues(
            "spring.application.name=" + prefix,
            "spring.datasource.url=" + postgres.getJdbcUrl(),
            "spring.datasource.username=" + postgres.getUsername(),
            "spring.datasource.password=" + postgres.getPassword(),
            "spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.producer.properties.max.block.ms=2000",
            "spring.kafka.producer.properties.request.timeout.ms=1000",
            "spring.kafka.producer.properties.delivery.timeout.ms=3000",
            "cqrs.kafka.prefix=" + prefix,
            "cqrs.kafka.commands.enabled=false",
            "cqrs.kafka.queries.enabled=false",
            "cqrs.jdbc.initialize-schema=always",
            "cqrs.outbox.enabled=true",
            "cqrs.outbox.relay.interval=200ms",
            "cqrs.outbox.relay.max-attempts=2")
        .withBean(OrderPlacedCollector.class, OrderPlacedCollector::new)
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                CqrsAutoConfiguration.class,
                CqrsSerializationAutoConfiguration.class,
                KafkaAutoConfiguration.class,
                KafkaCqrsAutoConfiguration.class,
                KafkaEventBusAutoConfiguration.class,
                CqrsJdbcOutboxAutoConfiguration.class));
  }

  private static MessageContext.Scope correlation(String id) {
    return MessageContext.scope(MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, id));
  }

  @Test
  void committedEventsArePublishedAndRolledBackOnesAreNot() {
    runner("outbox-it-commit")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              OutboxEventBus outbox = context.getBean(OutboxEventBus.class);
              TransactionTemplate tx =
                  new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
              OrderPlacedCollector collector = context.getBean(OrderPlacedCollector.class);

              try (MessageContext.Scope ignored = correlation("corr-1")) {
                tx.executeWithoutResult(s -> outbox.publish(new TestOrderPlaced("committed")));
                tx.executeWithoutResult(
                    s -> {
                      outbox.publish(new TestOrderPlaced("rolled-back"));
                      s.setRollbackOnly();
                    });
                tx.executeWithoutResult(s -> outbox.publish(new TestOrderPlaced("committed-2")));
              }

              Set<String> received = new HashSet<>();
              received.add(collector.received.poll(60, TimeUnit.SECONDS));
              received.add(collector.received.poll(60, TimeUnit.SECONDS));
              assertThat(received)
                  .containsExactlyInAnyOrder("committed|corr-1", "committed-2|corr-1");
              assertThat(collector.received.poll(3, TimeUnit.SECONDS)).isNull();
              assertThat(
                      jdbc.queryForList(
                          "SELECT DISTINCT event_name FROM cqrs_outbox", String.class))
                  .containsExactly(
                      context
                          .getBean(MessageNamingStrategy.class)
                          .eventName(TestOrderPlaced.class));
              await()
                  .atMost(Duration.ofSeconds(30))
                  .until(
                      () ->
                          jdbc.queryForObject(
                                  "SELECT COUNT(*) FROM cqrs_outbox WHERE published_at IS NOT NULL",
                                  Integer.class)
                              == 2);
            });
  }

  @Test
  void rowsStayPendingWhileTheBrokerIsDownAndArePublishedWhenItReturns() {
    runner("outbox-it-down")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              OutboxEventBus outbox = context.getBean(OutboxEventBus.class);
              TransactionTemplate tx =
                  new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
              OrderPlacedCollector collector = context.getBean(OrderPlacedCollector.class);
              TestOrderPlaced event = new TestOrderPlaced("while-down");

              kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
              try {
                tx.executeWithoutResult(s -> outbox.publish(event));
                // max-attempts=2: more than two failed attempts proves a publish failure never
                // sets the row aside.
                await()
                    .atMost(Duration.ofSeconds(60))
                    .until(() -> attempts(event.getEventId()) > 2);
                assertThat(
                        jdbc.queryForMap(
                            "SELECT published_at, failed_at FROM cqrs_outbox WHERE event_id = ?",
                            event.getEventId()))
                    .containsEntry("published_at", null)
                    .containsEntry("failed_at", null);
              } finally {
                kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
              }

              assertThat(collector.received.poll(120, TimeUnit.SECONDS)).startsWith("while-down|");
              await()
                  .atMost(Duration.ofSeconds(30))
                  .until(
                      () ->
                          jdbc.queryForObject(
                                  "SELECT published_at FROM cqrs_outbox WHERE event_id = ?",
                                  java.sql.Timestamp.class,
                                  event.getEventId())
                              != null);
            });
  }

  private static int attempts(String eventId) {
    return jdbc.queryForObject(
        "SELECT attempts FROM cqrs_outbox WHERE event_id = ?", Integer.class, eventId);
  }
}
