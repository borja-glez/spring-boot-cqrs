package com.borjaglez.cqrs.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.fixtures.TestKeyedCommand;
import com.borjaglez.cqrs.kafka.fixtures.TestKeyedEvent;
import com.borjaglez.cqrs.kafka.fixtures.TestKeyedQuery;
import com.borjaglez.cqrs.kafka.fixtures.TestOtherKeyedEvent;
import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
import com.borjaglez.cqrs.kafka.infrastructure.DefaultKafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageKind;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

class KafkaMessagePublisherTest {

  private KafkaTemplate<String, byte[]> kafkaTemplate;
  private MessageSerializer serializer;
  private KafkaPartitionKeyStrategy partitionKeyStrategy;
  private MessageNamingStrategy messageNamingStrategy;
  private KafkaMessagePublisher publisher;

  @BeforeEach
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    serializer = mock(MessageSerializer.class);
    partitionKeyStrategy = mock(KafkaPartitionKeyStrategy.class);
    messageNamingStrategy = mock(MessageNamingStrategy.class);
    publisher =
        new KafkaMessagePublisher(
            kafkaTemplate, serializer, partitionKeyStrategy, messageNamingStrategy);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  void publishAddsHeadersForCommands() {
    TestCommand command = new TestCommand("value");
    byte[] payload = "command".getBytes(UTF_8);
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command))
        .thenReturn("command-key");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.command.create");
    when(serializer.serialize(command)).thenReturn(payload);

    publisher.publish("cqrs.commands", command);

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(record.topic()).isEqualTo("cqrs.commands");
    assertThat(record.key()).isEqualTo("command-key");
    assertThat(record.value()).isEqualTo(payload);
    assertThat(header(record, KafkaMessageHeaders.MESSAGE_KIND)).isEqualTo("COMMAND");
    assertThat(header(record, KafkaMessageHeaders.MESSAGE_NAME)).isEqualTo("sales.command.create");
    assertThat(header(record, KafkaMessageHeaders.PAYLOAD_TYPE))
        .isEqualTo(TestCommand.class.getName());
  }

  @Test
  void publishAddsHeadersForEvents() {
    TestEvent event = new TestEvent("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.EVENT, event)).thenReturn("event-key");
    when(messageNamingStrategy.eventName(TestEvent.class)).thenReturn("sales.event.created");
    when(serializer.serialize(event)).thenReturn("event".getBytes(UTF_8));

    publisher.publish("cqrs.events", event);

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(header(record, KafkaMessageHeaders.MESSAGE_KIND)).isEqualTo("EVENT");
    assertThat(header(record, KafkaMessageHeaders.MESSAGE_NAME)).isEqualTo("sales.event.created");
  }

  @Test
  void publishAddsHeadersForQueries() {
    TestQuery query = new TestQuery("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("query-key");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.query.find");
    when(serializer.serialize(query)).thenReturn("query".getBytes(UTF_8));

    publisher.publish("cqrs.queries", query);

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(header(record, KafkaMessageHeaders.MESSAGE_KIND)).isEqualTo("QUERY");
    assertThat(header(record, KafkaMessageHeaders.MESSAGE_NAME)).isEqualTo("sales.query.find");
  }

  @Test
  void publishReplySendsANullResultAsAnEmptyBody() {
    publisher.publishReply("cqrs.replies", "corr-1", null);

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(record.topic()).isEqualTo("cqrs.replies");
    assertThat(record.key()).isEqualTo("corr-1");
    assertThat(record.value()).isEmpty();
    assertThat(header(record, KafkaMessageHeaders.CORRELATION_ID)).isEqualTo("corr-1");
    assertThat(record.headers().lastHeader(KafkaMessageHeaders.PAYLOAD_TYPE)).isNull();
    verifyNoInteractions(serializer);
  }

  @Test
  void publishReplyUsesPayloadTypeWhenPayloadIsPresent() {
    when(serializer.serialize("done")).thenReturn("done".getBytes(UTF_8));

    publisher.publishReply("cqrs.replies", "corr-1", "done");

    assertThat(header(sentRecord(), KafkaMessageHeaders.PAYLOAD_TYPE))
        .isEqualTo(String.class.getName());
  }

  @Test
  void publishErrorReplyPublishesErrorHeaders() {
    publisher.publishErrorReply("cqrs.replies", "corr-1", new IllegalStateException("boom"));

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(new String(record.value(), UTF_8)).isEqualTo("boom");
    assertThat(header(record, KafkaMessageHeaders.CORRELATION_ID)).isEqualTo("corr-1");
    assertThat(header(record, KafkaMessageHeaders.ERROR)).isEqualTo("true");
    assertThat(header(record, KafkaMessageHeaders.PAYLOAD_TYPE)).isEqualTo(String.class.getName());
  }

  @Test
  void publishErrorReplyDescribesAnExceptionWithoutMessageByItsClass() {
    publisher.publishErrorReply("cqrs.replies", "corr-1", new NullPointerException());

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(new String(record.value(), UTF_8)).isEqualTo(NullPointerException.class.getName());
    assertThat(header(record, KafkaMessageHeaders.ERROR_TYPE))
        .isEqualTo(NullPointerException.class.getName());
  }

  @Test
  void publishRethrowsRuntimeCauseFromKafkaSend() {
    TestCommand command = new TestCommand("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command))
        .thenReturn("command-key");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.command.create");
    when(serializer.serialize(command)).thenReturn("command".getBytes(UTF_8));
    CompletableFuture<Object> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IllegalStateException("boom"));
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn((CompletableFuture) failedFuture);

    assertThatThrownBy(() -> publisher.publish("cqrs.commands", command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void publishWrapsNonRuntimeCauseFromKafkaSend() {
    TestCommand command = new TestCommand("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command))
        .thenReturn("command-key");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.command.create");
    when(serializer.serialize(command)).thenReturn("command".getBytes(UTF_8));
    CompletableFuture<Object> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IOException("boom"));
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn((CompletableFuture) failedFuture);

    assertThatThrownBy(() -> publisher.publish("cqrs.commands", command))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IOException.class)
        .rootCause()
        .hasMessage("boom");
  }

  @Test
  void publishPropagatesCurrentContextAsHeaders() {
    TestCommand command = new TestCommand("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command))
        .thenReturn("command-key");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.command.create");
    when(serializer.serialize(command)).thenReturn("command".getBytes(UTF_8));

    MessageContext ctx =
        MessageContext.empty().with("correlationId", "cid").with("tenantId", "acme");
    try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
      publisher.publish("cqrs.commands", command);
    }

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(header(record, "cqrs.context.correlationId")).isEqualTo("cid");
    assertThat(header(record, "cqrs.context.tenantId")).isEqualTo("acme");
  }

  @Test
  void publishUsesCustomContextHeaderPrefix() {
    KafkaMessagePublisher custom =
        new KafkaMessagePublisher(
            kafkaTemplate, serializer, partitionKeyStrategy, messageNamingStrategy, "ctx.");
    TestCommand command = new TestCommand("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command)).thenReturn("k");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("n");
    when(serializer.serialize(command)).thenReturn(new byte[0]);

    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.empty().with("correlationId", "cid"))) {
      custom.publish("topic", command);
    }
    assertThat(header(sentRecord(), "ctx.correlationId")).isEqualTo("cid");
  }

  @Test
  void nullContextHeaderPrefixFallsBackToDefault() {
    KafkaMessagePublisher custom =
        new KafkaMessagePublisher(
            kafkaTemplate, serializer, partitionKeyStrategy, messageNamingStrategy, null);
    TestCommand command = new TestCommand("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command)).thenReturn("k");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("n");
    when(serializer.serialize(command)).thenReturn(new byte[0]);

    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.empty().with("correlationId", "cid"))) {
      custom.publish("topic", command);
    }
    assertThat(header(sentRecord(), "cqrs.context.correlationId")).isEqualTo("cid");
  }

  @Test
  void publishRejectsUnsupportedMessageTypes() {
    assertThatThrownBy(() -> publisher.publish("cqrs.misc", new Object()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported CQRS message type");
  }

  @Test
  void eventsOfDifferentTypesDeclaringTheSameKeyArePublishedWithTheSameRecordKey() {
    KafkaMessagePublisher keyed =
        new KafkaMessagePublisher(
            kafkaTemplate,
            serializer,
            new DefaultKafkaPartitionKeyStrategy(new KafkaCqrsProperties(), messageNamingStrategy),
            messageNamingStrategy);
    TestKeyedEvent placed = new TestKeyedEvent("order-1");
    TestOtherKeyedEvent cancelled = new TestOtherKeyedEvent("order-1");
    when(messageNamingStrategy.eventName(TestKeyedEvent.class)).thenReturn("orders.order.placed");
    when(messageNamingStrategy.eventName(TestOtherKeyedEvent.class))
        .thenReturn("orders.order.cancelled");
    when(serializer.serialize(any())).thenReturn(new byte[0]);

    keyed.publish("cqrs.events", placed);
    ProducerRecord<String, byte[]> first = sentRecord();
    keyed.publish("cqrs.events", cancelled);
    ProducerRecord<String, byte[]> second = sentRecord();

    assertThat(first.key()).isEqualTo("order-1");
    assertThat(second.key()).isEqualTo("order-1");
  }

  @Test
  void publishAddsTheDeclaredKeyAsHeader() {
    TestKeyedEvent event = new TestKeyedEvent("order-1");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.EVENT, event)).thenReturn("order-1");
    when(messageNamingStrategy.eventName(TestKeyedEvent.class)).thenReturn("orders.order.placed");
    when(serializer.serialize(event)).thenReturn(new byte[0]);

    publisher.publish("cqrs.events", event);

    assertThat(header(sentRecord(), KafkaMessageHeaders.MESSAGE_KEY)).isEqualTo("order-1");
  }

  @Test
  void publishOmitsTheKeyHeaderWhenTheMessageDeclaresNoKey() {
    TestEvent event = new TestEvent("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.EVENT, event)).thenReturn("event-key");
    when(messageNamingStrategy.eventName(TestEvent.class)).thenReturn("sales.event.created");
    when(serializer.serialize(event)).thenReturn(new byte[0]);

    publisher.publish("cqrs.events", event);

    assertThat(sentRecord().headers().lastHeader(KafkaMessageHeaders.MESSAGE_KEY)).isNull();
  }

  @Test
  void publishOmitsTheKeyHeaderWhenTheDeclaredKeyIsBlank() {
    TestKeyedCommand command = new TestKeyedCommand(" ");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command)).thenReturn("k");
    when(messageNamingStrategy.commandName(TestKeyedCommand.class)).thenReturn("n");
    when(serializer.serialize(command)).thenReturn(new byte[0]);

    publisher.publish("cqrs.commands", command);

    assertThat(sentRecord().headers().lastHeader(KafkaMessageHeaders.MESSAGE_KEY)).isNull();
  }

  @Test
  void publishOmitsTheKeyHeaderForQueries() {
    TestKeyedQuery query = new TestKeyedQuery("order-1");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("k");
    when(messageNamingStrategy.queryName(TestKeyedQuery.class)).thenReturn("n");
    when(serializer.serialize(query)).thenReturn(new byte[0]);

    publisher.publish("cqrs.queries", query);

    assertThat(sentRecord().headers().lastHeader(KafkaMessageHeaders.MESSAGE_KEY)).isNull();
  }

  private ProducerRecord<String, byte[]> sentRecord() {
    return org.mockito.Mockito.mockingDetails(kafkaTemplate).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("send"))
        .reduce((first, second) -> second)
        .map(invocation -> (ProducerRecord<String, byte[]>) invocation.getArgument(0))
        .orElseThrow();
  }

  private String header(ProducerRecord<String, byte[]> record, String name) {
    return new String(record.headers().lastHeader(name).value(), UTF_8);
  }
}
