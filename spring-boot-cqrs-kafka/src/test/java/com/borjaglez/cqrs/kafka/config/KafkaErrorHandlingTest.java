package com.borjaglez.cqrs.kafka.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;

import com.borjaglez.cqrs.kafka.consumer.UnprocessableRecordException;
import com.borjaglez.cqrs.kafka.infrastructure.DefaultKafkaTopicNamingStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;

class KafkaErrorHandlingTest {

  private static final String DEAD_LETTER_TOPIC = "cqrs.orders.events.dlt";

  private KafkaOperations<String, byte[]> template;
  private KafkaCqrsProperties.ErrorHandlingProperties properties;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    template = mock(KafkaOperations.class);
    when(template.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    properties = new KafkaCqrsProperties.ErrorHandlingProperties();
    properties.getBackOff().setInitialInterval(Duration.ofMillis(1));
    properties.getBackOff().setMaxInterval(Duration.ofMillis(1));
  }

  @Test
  void defaultsFollowTheDecidedPolicy() {
    KafkaCqrsProperties.ErrorHandlingProperties defaults =
        new KafkaCqrsProperties().getErrorHandling();

    assertThat(defaults.getMaxAttempts()).isEqualTo(3);
    assertThat(defaults.getBackOff().getInitialInterval()).isEqualTo(Duration.ofSeconds(1));
    assertThat(defaults.getBackOff().getMultiplier()).isEqualTo(2.0);
    assertThat(defaults.getBackOff().getMaxInterval()).isEqualTo(Duration.ofSeconds(10));
    assertThat(defaults.getDeadLetter().isEnabled()).isTrue();
    assertThat(defaults.getDeadLetter().getPartitions()).isEqualTo(1);
    assertThat(defaults.getDeadLetter().getReplicas()).isEqualTo((short) 1);
  }

  @Test
  void retriesUpToMaxAttemptsAndThenPublishesToTheDeadLetterTopic() {
    DefaultErrorHandler errorHandler =
        KafkaErrorHandling.errorHandler(properties, template, DEAD_LETTER_TOPIC);
    ConsumerRecord<String, byte[]> record = record();
    Exception failure = listenerFailure(new IllegalStateException("db down"));

    assertThat(handle(errorHandler, failure, record)).isFalse();
    assertThat(handle(errorHandler, failure, record)).isFalse();
    verify(template, never()).send(any(ProducerRecord.class));
    assertThat(handle(errorHandler, failure, record)).isTrue();

    ProducerRecord<String, byte[]> deadLetter = sentRecord();
    assertThat(deadLetter.topic()).isEqualTo(DEAD_LETTER_TOPIC);
    assertThat(deadLetter.partition()).isNull();
    assertThat(deadLetter.key()).isEqualTo("key");
    assertThat(header(deadLetter.headers(), KafkaMessageHeaders.PAYLOAD_TYPE))
        .isEqualTo("com.example.OrderPlaced");
    assertThat(header(deadLetter.headers(), KafkaMessageHeaders.ERROR_TYPE))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(header(deadLetter.headers(), KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
  }

  @Test
  void maxAttemptsDrivesTheNumberOfDeliveries() {
    properties.setMaxAttempts(1);
    DefaultErrorHandler errorHandler =
        KafkaErrorHandling.errorHandler(properties, template, DEAD_LETTER_TOPIC);

    assertThat(handle(errorHandler, listenerFailure(new IllegalStateException()), record()))
        .isTrue();
    verify(template, times(1)).send(any(ProducerRecord.class));
  }

  @Test
  void unprocessableRecordsAreNotRetried() {
    DefaultErrorHandler errorHandler =
        KafkaErrorHandling.errorHandler(properties, template, DEAD_LETTER_TOPIC);

    boolean recovered =
        handle(
            errorHandler,
            listenerFailure(new UnprocessableRecordException("Missing payload type")),
            record());

    assertThat(recovered).isTrue();
    assertThat(sentRecord().topic()).isEqualTo(DEAD_LETTER_TOPIC);
  }

  @Test
  void withoutDeadLetteringExhaustedRecordsAreSkipped() {
    properties.setMaxAttempts(1);
    properties.getDeadLetter().setEnabled(false);
    DefaultErrorHandler errorHandler =
        KafkaErrorHandling.errorHandler(properties, template, DEAD_LETTER_TOPIC);

    assertThat(handle(errorHandler, listenerFailure(new IllegalStateException()), record()))
        .isTrue();
    verify(template, never()).send(any(ProducerRecord.class));
  }

  @Test
  void rejectsFewerThanOneAttempt() {
    properties.setMaxAttempts(0);

    assertThatThrownBy(() -> KafkaErrorHandling.errorHandler(properties, template, "dlt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cqrs.kafka.error-handling.max-attempts must be at least 1, but was 0");
  }

  @Test
  void rejectsAMultiplierBelowOne() {
    properties.getBackOff().setMultiplier(0.5);

    assertThatThrownBy(() -> KafkaErrorHandling.errorHandler(properties, template, "dlt"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void failureHeadersDescribeTheHandlerFailure() {
    ConsumerRecord<String, byte[]> record = record();
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaHeaders.DELIVERY_ATTEMPT, ByteBuffer.allocate(4).putInt(3).array()));
    Instant before = Instant.now();

    Headers headers =
        KafkaErrorHandling.failureHeaders(
            record, listenerFailure(new IllegalStateException("db down")));

    assertThat(header(headers, KafkaMessageHeaders.ERROR_TYPE))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(header(headers, KafkaMessageHeaders.ERROR_MESSAGE)).isEqualTo("db down");
    assertThat(header(headers, KafkaMessageHeaders.ERROR_ATTEMPTS)).isEqualTo("3");
    assertThat(Instant.parse(header(headers, KafkaMessageHeaders.ERROR_TIMESTAMP)))
        .isAfterOrEqualTo(before);
  }

  @Test
  void failureHeadersCountOneAttemptWithoutDeliveryAttemptHeader() {
    Headers headers = KafkaErrorHandling.failureHeaders(record(), new IllegalStateException());

    assertThat(header(headers, KafkaMessageHeaders.ERROR_ATTEMPTS)).isEqualTo("1");
    assertThat(headers.lastHeader(KafkaMessageHeaders.ERROR_MESSAGE)).isNull();
  }

  @Test
  void failureHeadersTruncateLongMessages() {
    String longMessage = "x".repeat(KafkaMessageHeaders.MAX_ERROR_MESSAGE_LENGTH + 10);

    Headers headers =
        KafkaErrorHandling.failureHeaders(record(), new IllegalStateException(longMessage));

    assertThat(header(headers, KafkaMessageHeaders.ERROR_MESSAGE))
        .hasSize(KafkaMessageHeaders.MAX_ERROR_MESSAGE_LENGTH);
  }

  @Test
  void handlerFailureUnwrapsOnlyListenerWrappersWithACause() {
    IllegalStateException cause = new IllegalStateException("boom");
    ListenerExecutionFailedException withoutCause = new ListenerExecutionFailedException("x");

    assertThat(
            KafkaErrorHandling.handlerFailure(
                new ListenerExecutionFailedException("outer", listenerFailure(cause))))
        .isSameAs(cause);
    assertThat(KafkaErrorHandling.handlerFailure(withoutCause)).isSameAs(withoutCause);
    assertThat(KafkaErrorHandling.handlerFailure(cause)).isSameAs(cause);
  }

  @Test
  void configureBuildsTheErrorHandlerWhenTheApplicationHasNone() {
    ConcurrentMessageListenerContainer<String, byte[]> container = container();

    KafkaErrorHandling.configure(
        container, provider(null, null), provider(null, null), properties, template, "dlt");

    assertThat(container.getCommonErrorHandler()).isInstanceOf(DefaultErrorHandler.class);
    assertThat(container.getContainerProperties().isDeliveryAttemptHeader()).isTrue();
  }

  @Test
  void configurePrefersTheQualifiedErrorHandler() {
    ConcurrentMessageListenerContainer<String, byte[]> container = container();
    CommonErrorHandler qualified = mock(CommonErrorHandler.class);
    CommonErrorHandler unique = mock(CommonErrorHandler.class);

    KafkaErrorHandling.configure(
        container, provider(qualified, null), provider(null, unique), properties, template, "dlt");

    assertThat(container.getCommonErrorHandler()).isSameAs(qualified);
  }

  @Test
  void configureUsesTheApplicationsUniqueErrorHandler() {
    ConcurrentMessageListenerContainer<String, byte[]> container = container();
    CommonErrorHandler unique = mock(CommonErrorHandler.class);

    KafkaErrorHandling.configure(
        container, provider(null, null), provider(null, unique), properties, template, "dlt");

    assertThat(container.getCommonErrorHandler()).isSameAs(unique);
  }

  @Test
  void deadLetterTopicUsesTheDeadLetterProperties() {
    KafkaCqrsProperties cqrsProperties = new KafkaCqrsProperties();
    cqrsProperties.getErrorHandling().getDeadLetter().setPartitions(2);
    cqrsProperties.getErrorHandling().getDeadLetter().setReplicas((short) 3);

    NewTopic topic =
        KafkaErrorHandling.deadLetterTopic(
            cqrsProperties, new DefaultKafkaTopicNamingStrategy("cqrs"), "orders", "events");

    assertThat(topic.name()).isEqualTo("cqrs.orders.events.dlt");
    assertThat(topic.numPartitions()).isEqualTo(2);
    assertThat(topic.replicationFactor()).isEqualTo((short) 3);
  }

  @SuppressWarnings("unchecked")
  private static boolean handle(
      DefaultErrorHandler errorHandler, Exception failure, ConsumerRecord<String, byte[]> record) {
    return errorHandler.handleOne(
        failure, record, mock(Consumer.class), mock(MessageListenerContainer.class));
  }

  @SuppressWarnings("unchecked")
  private ProducerRecord<String, byte[]> sentRecord() {
    ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(template).send(captor.capture());
    return captor.getValue();
  }

  private static ConsumerRecord<String, byte[]> record() {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("cqrs.events", 0, 42L, "key", "{}".getBytes(UTF_8));
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.PAYLOAD_TYPE, "com.example.OrderPlaced".getBytes(UTF_8)));
    return record;
  }

  private static ListenerExecutionFailedException listenerFailure(Exception cause) {
    return new ListenerExecutionFailedException("Listener failed", cause);
  }

  private static String header(Headers headers, String name) {
    Header header = headers.lastHeader(name);
    return header == null ? null : new String(header.value(), UTF_8);
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMessageListenerContainer<String, byte[]> container() {
    return new ConcurrentMessageListenerContainer<>(
        mock(ConsumerFactory.class), new ContainerProperties("cqrs.events"));
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<CommonErrorHandler> provider(
      CommonErrorHandler available, CommonErrorHandler unique) {
    ObjectProvider<CommonErrorHandler> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(available);
    when(provider.getIfUnique()).thenReturn(unique);
    return provider;
  }
}
