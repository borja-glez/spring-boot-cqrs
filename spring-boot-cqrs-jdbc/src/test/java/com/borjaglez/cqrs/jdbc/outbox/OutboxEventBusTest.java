package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.fixtures.TestOrderPlaced;
import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class OutboxEventBusTest {

  private final MessageSerializer serializer =
      new JacksonMessageSerializer(new ObjectMapper().registerModule(new JavaTimeModule()));
  private final MessageNamingStrategy naming = new DefaultMessageNamingStrategy("");
  private final OutboxContextCodec codec = new OutboxContextCodec(serializer, OutboxTracing.noop());

  private EmbeddedDatabase dataSource;
  private JdbcTemplate jdbc;
  private TransactionTemplate tx;
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
    jdbc = new JdbcTemplate(dataSource);
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    bus =
        new OutboxEventBus(
            new OutboxStore(dataSource, OutboxStore.DEFAULT_TABLE_NAME), serializer, naming, codec);
  }

  @AfterEach
  void tearDown() {
    dataSource.shutdown();
  }

  private int rows() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM cqrs_outbox", Integer.class);
  }

  @Test
  void publishOutsideATransactionThrowsAndStoresNothing() {
    assertThatThrownBy(() -> bus.publish(new TestOrderPlaced("o-1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TestOrderPlaced.class.getName())
        .hasMessageContaining("transaction");

    assertThat(rows()).isZero();
  }

  @Test
  void publishStoresTheEventWithItsLogicalNameClassPayloadAndContext() {
    TestOrderPlaced event = new TestOrderPlaced("o-1");

    try (MessageContext.Scope ignored =
        MessageContext.scope(
            MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, "corr-1"))) {
      tx.executeWithoutResult(status -> bus.publish(event));
    }

    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM cqrs_outbox");
    assertThat(row.get("EVENT_ID")).isEqualTo(event.getEventId());
    assertThat(row.get("EVENT_NAME")).isEqualTo(naming.eventName(TestOrderPlaced.class));
    assertThat(row.get("EVENT_CLASS")).isEqualTo(TestOrderPlaced.class.getName());
    assertThat(serializer.deserialize((byte[]) row.get("PAYLOAD"), TestOrderPlaced.class))
        .extracting(TestOrderPlaced::getOrderId, Event::getEventId)
        .containsExactly("o-1", event.getEventId());
    AtomicReference<String> correlation = new AtomicReference<>();
    codec.runWithin(
        (byte[]) row.get("CONTEXT"),
        () -> correlation.set(MessageContext.current().correlationId()));
    assertThat(correlation).hasValue("corr-1");
  }

  @Test
  void rollbackDiscardsTheRow() {
    tx.executeWithoutResult(
        status -> {
          bus.publish(new TestOrderPlaced("o-1"));
          status.setRollbackOnly();
        });

    assertThat(rows()).isZero();
  }

  @Test
  void publishListStoresTheEventsInOrder() {
    tx.executeWithoutResult(
        status ->
            bus.publish(List.<Event>of(new TestOrderPlaced("o-1"), new TestOrderPlaced("o-2"))));

    assertThat(
            jdbc.queryForList("SELECT payload FROM cqrs_outbox ORDER BY id", byte[].class).stream()
                .map(
                    payload -> serializer.deserialize(payload, TestOrderPlaced.class).getOrderId()))
        .containsExactly("o-1", "o-2");
  }
}
