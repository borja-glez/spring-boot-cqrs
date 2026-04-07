package com.borjaglez.cqrs.kafka.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

class KafkaRequestReplyClientTest {

  private KafkaTemplate<String, byte[]> kafkaTemplate;
  private MessageSerializer serializer;
  private KafkaPartitionKeyStrategy partitionKeyStrategy;
  private MessageNamingStrategy messageNamingStrategy;
  private KafkaRequestReplyClient client;
  private AtomicReference<ProducerRecord<String, byte[]>> sentRecord;
  private CountDownLatch sendLatch;

  @BeforeEach
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    serializer = mock(MessageSerializer.class);
    partitionKeyStrategy = mock(KafkaPartitionKeyStrategy.class);
    messageNamingStrategy = mock(MessageNamingStrategy.class);
    sentRecord = new AtomicReference<>();
    sendLatch = new CountDownLatch(1);
    client =
        new KafkaRequestReplyClient(
            kafkaTemplate,
            serializer,
            partitionKeyStrategy,
            messageNamingStrategy,
            "cqrs.orders.replies",
            Duration.ofSeconds(5));
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenAnswer(
            invocation -> {
              sentRecord.set(invocation.getArgument(0));
              sendLatch.countDown();
              return CompletableFuture.completedFuture(null);
            });
  }

  @Test
  void shouldSendRequestWithCorrelationIdAndDeserializeReplyUsingPayloadTypeHeader()
      throws InterruptedException {
    TestQuery query = new TestQuery("abc");
    ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<String>() {};
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));
    when(serializer.deserialize("response".getBytes(UTF_8), responseType)).thenReturn("done");
    AtomicReference<ProducerRecord<String, byte[]>> recordReference = new AtomicReference<>();
    CountDownLatch sendLatch = new CountDownLatch(1);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenAnswer(
            invocation -> {
              recordReference.set(invocation.getArgument(0));
              sendLatch.countDown();
              return CompletableFuture.completedFuture(null);
            });

    CompletableFuture<Object> invocation =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return client.sendAndReceive(
                    null, "cqrs.queries", query, responseType, KafkaRequestMode.REPLY);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(sendLatch.await(2, TimeUnit.SECONDS)).isTrue();
    ProducerRecord<String, byte[]> requestRecord = recordReference.get();
    assertThat(requestRecord.topic()).isEqualTo("cqrs.queries");
    assertThat(requestRecord.key()).isEqualTo("sales.order.find");

    String correlationId =
        new String(
            requestRecord.headers().lastHeader(KafkaMessageHeaders.CORRELATION_ID).value(), UTF_8);
    assertThat(correlationId).isNotBlank();

    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.orders.replies", 0, 0L, correlationId, "response".getBytes(UTF_8));
    reply.headers()
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, correlationId.getBytes(UTF_8)))
        .add(new RecordHeader(KafkaMessageHeaders.PAYLOAD_TYPE, String.class.getName().getBytes(UTF_8)));
    client.handleReply(reply);

    assertThat(invocation.join()).isEqualTo("done");
  }

  @Test
  void shouldDeserializeReplyUsingParameterizedTypeReferenceWithoutDroppingGenerics()
      throws InterruptedException {
    TestQuery query = new TestQuery("abc");
    ParameterizedTypeReference<List<String>> responseType =
        new ParameterizedTypeReference<List<String>>() {};
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));
    when(serializer.deserialize("response".getBytes(UTF_8), responseType))
        .thenReturn(List.of("done"));
    AtomicReference<ProducerRecord<String, byte[]>> recordReference = new AtomicReference<>();
    CountDownLatch sendLatch = new CountDownLatch(1);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenAnswer(
            invocation -> {
              recordReference.set(invocation.getArgument(0));
              sendLatch.countDown();
              return CompletableFuture.completedFuture(null);
            });

    CompletableFuture<Object> invocation =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return client.sendAndReceive(
                    null, "cqrs.queries", query, responseType, KafkaRequestMode.REPLY);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(sendLatch.await(2, TimeUnit.SECONDS)).isTrue();
    ProducerRecord<String, byte[]> requestRecord = recordReference.get();
    String correlationId =
        new String(
            requestRecord.headers().lastHeader(KafkaMessageHeaders.CORRELATION_ID).value(), UTF_8);

    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.orders.replies", 0, 0L, correlationId, "response".getBytes(UTF_8));
    reply.headers().add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, correlationId.getBytes(UTF_8)));
    client.handleReply(reply);

    assertThat(invocation.join()).isEqualTo(List.of("done"));
  }

  @Test
  void shouldDeserializeReplyUsingNestedParameterizedTypeReferenceWithoutDroppingGenerics()
      throws InterruptedException {
    TestQuery query = new TestQuery("abc");
    ParameterizedTypeReference<Map<String, List<String>>> responseType =
        new ParameterizedTypeReference<Map<String, List<String>>>() {};
    Map<String, List<String>> expectedResponse = Map.of("items", List.of("done", "again"));
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));
    when(serializer.deserialize("response".getBytes(UTF_8), responseType)).thenReturn(expectedResponse);
    AtomicReference<ProducerRecord<String, byte[]>> recordReference = new AtomicReference<>();
    CountDownLatch sendLatch = new CountDownLatch(1);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenAnswer(
            invocation -> {
              recordReference.set(invocation.getArgument(0));
              sendLatch.countDown();
              return CompletableFuture.completedFuture(null);
            });

    CompletableFuture<Object> invocation =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return client.sendAndReceive(
                    null, "cqrs.queries", query, responseType, KafkaRequestMode.REPLY);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(sendLatch.await(2, TimeUnit.SECONDS)).isTrue();
    ProducerRecord<String, byte[]> requestRecord = recordReference.get();
    String correlationId =
        new String(
            requestRecord.headers().lastHeader(KafkaMessageHeaders.CORRELATION_ID).value(), UTF_8);

    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.orders.replies", 0, 0L, correlationId, "response".getBytes(UTF_8));
    reply.headers().add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, correlationId.getBytes(UTF_8)));
    client.handleReply(reply);

    assertThat(invocation.join()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldRaiseRemoteErrorFromReplyHeader() throws InterruptedException {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));
    AtomicReference<ProducerRecord<String, byte[]>> recordReference = new AtomicReference<>();
    CountDownLatch sendLatch = new CountDownLatch(1);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenAnswer(
            invocation -> {
              recordReference.set(invocation.getArgument(0));
              sendLatch.countDown();
              return CompletableFuture.completedFuture(null);
            });

    CompletableFuture<Object> invocation =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return client.sendAndReceive(
                    null, "cqrs.queries", query, null, KafkaRequestMode.REPLY);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertThat(sendLatch.await(2, TimeUnit.SECONDS)).isTrue();
    ProducerRecord<String, byte[]> requestRecord = recordReference.get();
    String correlationId =
        new String(
            requestRecord.headers().lastHeader(KafkaMessageHeaders.CORRELATION_ID).value(), UTF_8);

    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.orders.replies", 0, 0L, correlationId, "boom".getBytes(UTF_8));
    reply.headers()
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, correlationId.getBytes(UTF_8)))
        .add(new RecordHeader(KafkaMessageHeaders.ERROR, "true".getBytes(UTF_8)));
    client.handleReply(reply);

    assertThatThrownBy(invocation::join)
        .hasCauseInstanceOf(RuntimeException.class)
        .rootCause()
        .hasMessage("Remote handler error: boom");
  }

  @Test
  void shouldUseProvidedMessageNameWithoutCallingNamingStrategy() throws Exception {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive("custom.name", "cqrs.queries", query, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    assertThat(header(requestRecord, KafkaMessageHeaders.MESSAGE_NAME)).isEqualTo("custom.name");
    verify(messageNamingStrategy, never()).queryName(TestQuery.class);

    ConsumerRecord<String, byte[]> reply =
        replyWithPayloadType(requestRecord, String.class.getName(), "response".getBytes(UTF_8));
    when(serializer.deserialize("response".getBytes(UTF_8), String.class)).thenReturn("done");
    client.handleReply(reply);

    assertThat(invocation.join()).isEqualTo("done");
  }

  @Test
  void shouldResolveMessageNameWhenProvidedNameIsBlank() throws Exception {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive("   ", "cqrs.queries", query, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    assertThat(header(requestRecord, KafkaMessageHeaders.MESSAGE_NAME)).isEqualTo("sales.order.find");

    ConsumerRecord<String, byte[]> reply =
        replyWithPayloadType(requestRecord, String.class.getName(), "response".getBytes(UTF_8));
    when(serializer.deserialize("response".getBytes(UTF_8), String.class)).thenReturn("done");
    client.handleReply(reply);

    assertThat(invocation.join()).isEqualTo("done");
  }

  @Test
  void shouldResolveCommandMessageNameWhenResponseTypeIsNull() throws Exception {
    TestCommand command = new TestCommand("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.COMMAND, command))
        .thenReturn("sales.order.create");
    when(messageNamingStrategy.commandName(TestCommand.class)).thenReturn("sales.order.create");
    when(serializer.serialize(command)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive(null, "cqrs.commands", command, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    assertThat(header(requestRecord, KafkaMessageHeaders.MESSAGE_KIND)).isEqualTo("COMMAND");
    assertThat(header(requestRecord, KafkaMessageHeaders.MESSAGE_NAME)).isEqualTo("sales.order.create");

    ConsumerRecord<String, byte[]> reply =
        replyWithPayloadType(requestRecord, String.class.getName(), "response".getBytes(UTF_8));
    when(serializer.deserialize("response".getBytes(UTF_8), String.class)).thenReturn("done");
    client.handleReply(reply);

    assertThat(invocation.join()).isEqualTo("done");
  }

  @Test
  void shouldResolveEventMessageNameAndReturnNullForEmptyReply() throws Exception {
    TestEvent event = new TestEvent("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.EVENT, event)).thenReturn("sales.order.created");
    when(messageNamingStrategy.eventName(TestEvent.class)).thenReturn("sales.order.created");
    when(serializer.serialize(event)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive(null, "cqrs.events", event, null, KafkaRequestMode.WAIT));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    assertThat(header(requestRecord, KafkaMessageHeaders.MESSAGE_KIND)).isEqualTo("EVENT");
    assertThat(header(requestRecord, KafkaMessageHeaders.REQUEST_MODE)).isEqualTo("WAIT");

    client.handleReply(replyWithPayloadType(requestRecord, String.class.getName(), new byte[0]));

    assertThat(invocation.join()).isNull();
  }

  @Test
  void shouldReturnNullWhenReplyPayloadIsNull() throws Exception {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.orders.replies", 0, 0L, correlationId(requestRecord), null);
    reply.headers().add(
        new RecordHeader(
            KafkaMessageHeaders.CORRELATION_ID, correlationId(requestRecord).getBytes(UTF_8)));
    client.handleReply(reply);

    assertThat(invocation.join()).isNull();
  }

  @Test
  void shouldDefaultReplyPayloadTypeToStringWhenHeaderIsMissing() throws Exception {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>(
            "cqrs.orders.replies", 0, 0L, correlationId(requestRecord), "response".getBytes(UTF_8));
    reply.headers().add(
        new RecordHeader(
            KafkaMessageHeaders.CORRELATION_ID, correlationId(requestRecord).getBytes(UTF_8)));
    when(serializer.deserialize("response".getBytes(UTF_8), String.class)).thenReturn("done");
    client.handleReply(reply);

    assertThat(invocation.join()).isEqualTo("done");
  }

  @Test
  void shouldFailWhenReplyPayloadTypeCannotBeResolved() throws Exception {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    client.handleReply(
        replyWithPayloadType(requestRecord, "com.example.DoesNotExist", "response".getBytes(UTF_8)));

    Throwable thrown = catchThrowable(invocation::join);
    assertThat(thrown.getCause()).isInstanceOf(RuntimeException.class);
    assertThat(thrown.getCause().getCause())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to resolve Kafka reply payload type: com.example.DoesNotExist");
  }

  @Test
  void shouldThrowTimeoutExceptionWithResolvedMessageName() {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));
    KafkaRequestReplyClient timeoutClient =
        new KafkaRequestReplyClient(
            kafkaTemplate,
            serializer,
            partitionKeyStrategy,
            messageNamingStrategy,
            "cqrs.orders.replies",
            Duration.ofMillis(10));

    assertThatThrownBy(
            () -> timeoutClient.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Timed out waiting for Kafka reply for sales.order.find")
        .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
  }

  @Test
  void shouldRethrowCheckedCauseFromPendingReplyFuture() throws Exception {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    completePendingReplyExceptionally(correlationId(requestRecord), new IllegalArgumentException("boom"));

    assertThatThrownBy(invocation::join)
        .rootCause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("boom");
  }

  @Test
  void shouldWrapNonExceptionCauseFromPendingReplyFuture() throws Exception {
    TestQuery query = new TestQuery("abc");
    when(partitionKeyStrategy.partitionKey(KafkaMessageKind.QUERY, query)).thenReturn("sales.order.find");
    when(messageNamingStrategy.queryName(TestQuery.class)).thenReturn("sales.order.find");
    when(serializer.serialize(query)).thenReturn("request".getBytes(UTF_8));

    CompletableFuture<Object> invocation =
        startRequest(
            () -> client.sendAndReceive(null, "cqrs.queries", query, null, KafkaRequestMode.REPLY));

    ProducerRecord<String, byte[]> requestRecord = waitForSentRecord();
    completePendingReplyExceptionally(correlationId(requestRecord), new AssertionError("boom"));

    assertThatThrownBy(invocation::join)
        .hasCauseInstanceOf(RuntimeException.class)
        .rootCause()
        .hasMessage("boom");
  }

  @Test
  void shouldIgnoreRepliesWithoutCorrelationId() {
    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.orders.replies", 0, 0L, null, "response".getBytes(UTF_8));

    client.handleReply(reply);
  }

  @Test
  void shouldIgnoreRepliesWithUnknownCorrelationId() {
    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>("cqrs.orders.replies", 0, 0L, "unknown", "response".getBytes(UTF_8));
    reply.headers()
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, "unknown".getBytes(UTF_8)));

    client.handleReply(reply);
  }

  @Test
  void shouldRejectUnsupportedPayloadTypes() {
    assertThatThrownBy(
            () ->
                client.sendAndReceive(
                    null,
                    "cqrs.queries",
                    new Object(),
                    new ParameterizedTypeReference<String>() {},
                    KafkaRequestMode.REPLY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported CQRS message type");
  }

  private CompletableFuture<Object> startRequest(ThrowingSupplier supplier) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return supplier.get();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private ProducerRecord<String, byte[]> waitForSentRecord() throws InterruptedException {
    assertThat(sendLatch.await(2, TimeUnit.SECONDS)).isTrue();
    return sentRecord.get();
  }

  private ConsumerRecord<String, byte[]> replyWithPayloadType(
      ProducerRecord<String, byte[]> requestRecord, String payloadType, byte[] value) {
    ConsumerRecord<String, byte[]> reply =
        new ConsumerRecord<>(
            "cqrs.orders.replies", 0, 0L, correlationId(requestRecord), value);
    reply.headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.CORRELATION_ID, correlationId(requestRecord).getBytes(UTF_8)))
        .add(new RecordHeader(KafkaMessageHeaders.PAYLOAD_TYPE, payloadType.getBytes(UTF_8)));
    return reply;
  }

  @SuppressWarnings("unchecked")
  private void completePendingReplyExceptionally(String correlationId, Throwable error) throws Exception {
    Field repliesField = KafkaRequestReplyClient.class.getDeclaredField("replies");
    repliesField.setAccessible(true);
    Map<String, CompletableFuture<ConsumerRecord<String, byte[]>>> replies =
        (Map<String, CompletableFuture<ConsumerRecord<String, byte[]>>>) repliesField.get(client);
    replies.get(correlationId).completeExceptionally(error);
  }

  private String header(ProducerRecord<String, byte[]> record, String name) {
    return new String(record.headers().lastHeader(name).value(), UTF_8);
  }

  private String correlationId(ProducerRecord<String, byte[]> record) {
    return header(record, KafkaMessageHeaders.CORRELATION_ID);
  }

  @FunctionalInterface
  private interface ThrowingSupplier {
    Object get() throws Exception;
  }
}
