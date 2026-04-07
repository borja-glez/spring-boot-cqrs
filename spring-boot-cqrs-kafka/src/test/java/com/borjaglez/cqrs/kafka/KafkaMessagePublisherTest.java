package com.borjaglez.cqrs.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
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
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command)).thenReturn("command-key");
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
  void publishReplyUsesEmptyStringWhenPayloadIsNull() {
    when(serializer.serialize("")).thenReturn(new byte[0]);

    publisher.publishReply("cqrs.replies", "corr-1", null);

    ProducerRecord<String, byte[]> record = sentRecord();
    assertThat(record.topic()).isEqualTo("cqrs.replies");
    assertThat(record.key()).isEqualTo("corr-1");
    assertThat(header(record, KafkaMessageHeaders.CORRELATION_ID)).isEqualTo("corr-1");
    assertThat(header(record, KafkaMessageHeaders.PAYLOAD_TYPE)).isEqualTo(String.class.getName());
  }

  @Test
  void publishReplyUsesPayloadTypeWhenPayloadIsPresent() {
    when(serializer.serialize("done")).thenReturn("done".getBytes(UTF_8));

    publisher.publishReply("cqrs.replies", "corr-1", "done");

    assertThat(header(sentRecord(), KafkaMessageHeaders.PAYLOAD_TYPE)).isEqualTo(String.class.getName());
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
  void publishRethrowsRuntimeCauseFromKafkaSend() {
    TestCommand command = new TestCommand("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command)).thenReturn("command-key");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.command.create");
    when(serializer.serialize(command)).thenReturn("command".getBytes(UTF_8));
    CompletableFuture<Object> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IllegalStateException("boom"));
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn((CompletableFuture) failedFuture);

    assertThatThrownBy(() -> publisher.publish("cqrs.commands", command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void publishWrapsNonRuntimeCauseFromKafkaSend() {
    TestCommand command = new TestCommand("value");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command)).thenReturn("command-key");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.command.create");
    when(serializer.serialize(command)).thenReturn("command".getBytes(UTF_8));
    CompletableFuture<Object> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IOException("boom"));
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn((CompletableFuture) failedFuture);

    assertThatThrownBy(() -> publisher.publish("cqrs.commands", command))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IOException.class)
        .rootCause()
        .hasMessage("boom");
  }

  @Test
  void publishRejectsUnsupportedMessageTypes() {
    assertThatThrownBy(() -> publisher.publish("cqrs.misc", new Object()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported CQRS message type");
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
