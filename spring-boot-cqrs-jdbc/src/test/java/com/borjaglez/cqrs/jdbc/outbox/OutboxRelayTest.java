package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.TestOrderPlaced;
import com.borjaglez.cqrs.fixtures.TestRecordingEventBus;
import com.borjaglez.cqrs.jdbc.JdbcCqrsProperties.InitializeSchema;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class OutboxRelayTest {

  private final MessageSerializer serializer =
      new JacksonMessageSerializer(new ObjectMapper().registerModule(new JavaTimeModule()));
  private final MessageNamingStrategy naming = new DefaultMessageNamingStrategy("");
  private final TestRecordingEventBus target = new TestRecordingEventBus();
  private final OutboxContextCodecTest.FakeTracing tracing =
      new OutboxContextCodecTest.FakeTracing();
  private final OutboxContextCodec codec = new OutboxContextCodec(serializer, tracing);

  private EmbeddedDatabase dataSource;
  private DataSourceTransactionManager transactionManager;
  private JdbcTemplate jdbc;
  private OutboxStore store;
  private OutboxEventBus outbox;
  private OutboxEventTypeResolver resolver;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(UUID.randomUUID().toString())
            .build();
    new OutboxSchemaInitializer(dataSource, InitializeSchema.ALWAYS, OutboxStore.DEFAULT_TABLE_NAME)
        .afterPropertiesSet();
    transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    store = new OutboxStore(dataSource, OutboxStore.DEFAULT_TABLE_NAME);
    outbox = new OutboxEventBus(store, serializer, naming, codec);
    resolver =
        new OutboxEventTypeResolver(
            new EventHandlerRegistry(), naming, Set.of(), getClass().getClassLoader());
  }

  @AfterEach
  void tearDown() {
    dataSource.shutdown();
  }

  private OutboxRelay relay(EventBus bus, int batchSize, int maxAttempts) {
    return new OutboxRelay(
        store, resolver, serializer, codec, bus, transactionManager, batchSize, maxAttempts);
  }

  private TestOrderPlaced publishInTransaction(String orderId) {
    TestOrderPlaced event = new TestOrderPlaced(orderId);
    new TransactionTemplate(transactionManager).executeWithoutResult(s -> outbox.publish(event));
    return event;
  }

  private Map<String, Object> row(String eventId) {
    return jdbc.queryForMap("SELECT * FROM cqrs_outbox WHERE event_id = ?", eventId);
  }

  private static String orderId(Event event) {
    return ((TestOrderPlaced) event).getOrderId();
  }

  @Test
  void publishesPendingRowsInInsertionOrderAndMarksThemPublished() {
    TestOrderPlaced first = publishInTransaction("o-1");
    publishInTransaction("o-2");

    OutboxRelay.BatchResult result = relay(target, 10, 3).relayBatch();

    assertThat(target.published())
        .extracting(OutboxRelayTest::orderId)
        .containsExactly("o-1", "o-2");
    assertThat(target.published().get(0).getEventId()).isEqualTo(first.getEventId());
    assertThat(result).isEqualTo(new OutboxRelay.BatchResult(2, 2, 0, false, false));
    assertThat(row(first.getEventId()).get("PUBLISHED_AT")).isNotNull();
  }

  @Test
  void stopsTheBatchAtTheFirstPublishFailureAndRetriesItLater() {
    TestOrderPlaced first = publishInTransaction("o-1");
    TestOrderPlaced second = publishInTransaction("o-2");
    target.failNext(new IllegalStateException("broker down"));
    OutboxRelay relay = relay(target, 10, 3);

    OutboxRelay.BatchResult failed = relay.relayBatch();

    assertThat(failed).isEqualTo(new OutboxRelay.BatchResult(2, 0, 0, true, false));
    assertThat(row(first.getEventId()))
        .containsEntry("ATTEMPTS", 1)
        .containsEntry("LAST_ERROR", "java.lang.IllegalStateException: broker down")
        .containsEntry("PUBLISHED_AT", null)
        .containsEntry("FAILED_AT", null);
    assertThat(row(second.getEventId())).containsEntry("ATTEMPTS", 0);

    relay.relayBatch();

    assertThat(target.published())
        .extracting(OutboxRelayTest::orderId)
        .containsExactly("o-1", "o-2");
  }

  @Test
  void publishFailuresNeverSetARowAside() {
    TestOrderPlaced event = publishInTransaction("o-1");
    OutboxRelay relay = relay(target, 10, 2);
    for (int i = 0; i < 3; i++) {
      target.failNext(new IllegalStateException("broker down"));
      relay.relayBatch();
    }

    assertThat(row(event.getEventId()))
        .containsEntry("ATTEMPTS", 3)
        .containsEntry("FAILED_AT", null);

    relay.relayBatch();

    assertThat(target.published()).hasSize(1);
  }

  @Test
  void poisonRowIsSetAsideAfterMaxAttemptsAndLaterRowsContinue() {
    store.insert("poison", "missing", "com.example.Missing", new byte[] {1}, null);
    publishInTransaction("o-1");
    OutboxRelay relay = relay(target, 10, 2);

    OutboxRelay.BatchResult first = relay.relayBatch();
    OutboxRelay.BatchResult second = relay.relayBatch();

    assertThat(first).isEqualTo(new OutboxRelay.BatchResult(2, 0, 0, true, false));
    assertThat(second).isEqualTo(new OutboxRelay.BatchResult(2, 1, 1, false, false));
    assertThat(row("poison")).containsEntry("ATTEMPTS", 2);
    assertThat(row("poison").get("FAILED_AT")).isInstanceOf(Timestamp.class);
    assertThat((String) row("poison").get("LAST_ERROR")).contains("com.example.Missing");
    assertThat(target.published()).extracting(OutboxRelayTest::orderId).containsExactly("o-1");
  }

  @Test
  void undeserializablePayloadIsSetAsideImmediatelyWithOneAttempt() {
    store.insert(
        "garbage", "order-placed", TestOrderPlaced.class.getName(), "not json".getBytes(), null);

    OutboxRelay.BatchResult result = relay(target, 10, 1).relayBatch();

    assertThat(result.setAside()).isOne();
    assertThat((String) row("garbage").get("LAST_ERROR"))
        .startsWith("java.io.UncheckedIOException");
  }

  @Test
  void restoresTheStoredContextAndTraceWhilePublishing() {
    tracing.captured.put("traceparent", "00-abc-def-01");
    try (MessageContext.Scope ignored =
        MessageContext.scope(
            MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, "corr-1"))) {
      publishInTransaction("o-1");
    }
    tracing.captured.clear();

    relay(target, 10, 3).relayBatch();

    assertThat(target.correlationIds()).containsExactly("corr-1");
    assertThat(tracing.continued.get()).containsEntry("traceparent", "00-abc-def-01");
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void aFullBatchAsksToDrainAgain() {
    publishInTransaction("o-1");
    publishInTransaction("o-2");

    OutboxRelay.BatchResult full = relay(target, 2, 3).relayBatch();
    OutboxRelay.BatchResult empty = relay(target, 2, 3).relayBatch();

    assertThat(full.full()).isTrue();
    assertThat(full.drainAgain()).isTrue();
    assertThat(empty).isEqualTo(new OutboxRelay.BatchResult(0, 0, 0, false, false));
    assertThat(empty.drainAgain()).isFalse();
    assertThat(new OutboxRelay.BatchResult(2, 0, 0, true, true).drainAgain()).isFalse();
  }

  @Test
  void anErrorRollsBackTheWholeBatch() {
    TestOrderPlaced first = publishInTransaction("o-1");
    TestOrderPlaced second = publishInTransaction("o-2");
    EventBus failing = mock(EventBus.class);
    doNothing().doThrow(new AssertionError("fatal")).when(failing).publish(any(Event.class));

    assertThatThrownBy(() -> relay(failing, 10, 3).relayBatch()).isInstanceOf(AssertionError.class);

    assertThat(row(first.getEventId()))
        .containsEntry("PUBLISHED_AT", null)
        .containsEntry("ATTEMPTS", 0);
    assertThat(row(second.getEventId())).containsEntry("ATTEMPTS", 0);
  }

  @Test
  void errorWithoutMessageIsDescribedByItsClass() {
    TestOrderPlaced event = publishInTransaction("o-1");
    target.failNext(new IllegalStateException());

    relay(target, 10, 3).relayBatch();

    assertThat(row(event.getEventId()))
        .containsEntry("LAST_ERROR", "java.lang.IllegalStateException");
  }

  @Test
  void corruptStoredContextIsSetAsideAndLaterRowsContinue() {
    TestOrderPlaced bad = new TestOrderPlaced("bad");
    store.insert(
        bad.getEventId(),
        "order-placed",
        TestOrderPlaced.class.getName(),
        serializer.serialize(bad),
        "not json".getBytes());
    publishInTransaction("o-1");

    OutboxRelay.BatchResult result = relay(target, 10, 1).relayBatch();

    assertThat(result).isEqualTo(new OutboxRelay.BatchResult(2, 1, 1, false, false));
    assertThat(row(bad.getEventId())).containsEntry("ATTEMPTS", 1);
    assertThat(row(bad.getEventId()).get("FAILED_AT")).isInstanceOf(Timestamp.class);
    assertThat(target.published()).extracting(OutboxRelayTest::orderId).containsExactly("o-1");
  }

  @Test
  void exposesItsTargetAndRejectsInvalidLimits() {
    assertThat(relay(target, 1, 1).target()).isSameAs(target);
    assertThatThrownBy(() -> relay(target, 0, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batch-size");
    assertThatThrownBy(() -> relay(target, 1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max-attempts");
  }
}
