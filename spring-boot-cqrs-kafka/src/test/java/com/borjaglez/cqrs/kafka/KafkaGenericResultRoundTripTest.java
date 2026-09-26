package com.borjaglez.cqrs.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.kafka.consumer.KafkaCommandConsumer;
import com.borjaglez.cqrs.kafka.consumer.KafkaQueryConsumer;
import com.borjaglez.cqrs.kafka.fixtures.TestOrder;
import com.borjaglez.cqrs.kafka.fixtures.TestOrderListQuery;
import com.borjaglez.cqrs.kafka.fixtures.TestOrderResultCommand;
import com.borjaglez.cqrs.kafka.fixtures.TestResult;
import com.borjaglez.cqrs.kafka.infrastructure.DefaultKafkaTopicNamingStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.serialization.JacksonMessageSerializer;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Request/reply round trips of generic results over the Kafka wire format: requests and replies go
 * through the real client, consumers, publisher and Jackson serializer; only the broker is replaced
 * by handing each record to the consumer of its topic.
 *
 * <p>A reply carries only the runtime class of the result, so a generic result keeps its element
 * types only when the caller passes a {@link ParameterizedTypeReference} (finding C16).
 */
class KafkaGenericResultRoundTripTest {

  private static final String REPLY_TOPIC = "cqrs.orders.replies";

  private final KafkaTopicNamingStrategy topicNaming = new DefaultKafkaTopicNamingStrategy("cqrs");
  private KafkaRequestReplyClient client;
  private KafkaQueryConsumer queryConsumer;
  private KafkaCommandConsumer commandConsumer;
  private KafkaQueryBus queryBus;
  private KafkaCommandBus commandBus;
  private String lastReplyPayloadType;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
    MessageSerializer serializer = new JacksonMessageSerializer(new ObjectMapper());
    KafkaPartitionKeyStrategy partitionKeys = mock(KafkaPartitionKeyStrategy.class);
    MessageNamingStrategy messageNaming = new DefaultMessageNamingStrategy("test");
    KafkaMessagePublisher publisher =
        new KafkaMessagePublisher(template, serializer, partitionKeys, messageNaming);
    client =
        new KafkaRequestReplyClient(
            template, serializer, partitionKeys, messageNaming, REPLY_TOPIC, Duration.ofSeconds(5));
    queryConsumer = new KafkaQueryConsumer(queryRegistry(), List.of(), serializer, publisher);
    commandConsumer = new KafkaCommandConsumer(commandRegistry(), List.of(), serializer, publisher);
    queryBus = new KafkaQueryBus(client, topicNaming, "queries");
    commandBus = new KafkaCommandBus(publisher, client, topicNaming, "commands");
    when(template.send(any(ProducerRecord.class)))
        .thenAnswer(
            invocation -> {
              deliver(invocation.getArgument(0));
              return CompletableFuture.completedFuture(null);
            });
  }

  @Test
  void askReturnsTypedElementsWithATypeReference() {
    List<TestOrder> orders =
        queryBus.ask(
            new TestOrderListQuery(false), new ParameterizedTypeReference<List<TestOrder>>() {});

    assertThat(orders).containsExactly(new TestOrder("o-1"), new TestOrder("o-2"));
  }

  @Test
  void askReturnsMapsWithoutATypeReference() {
    List<Object> orders = queryBus.ask(new TestOrderListQuery(false));

    assertThat(orders).containsExactly(Map.of("id", "o-1"), Map.of("id", "o-2"));
  }

  @Test
  void askRoundTripsAStreamToListResult() {
    // Stream.toList() answers a JDK-internal list class, which the reply names as its type.
    List<TestOrder> typed =
        queryBus.ask(
            new TestOrderListQuery(true), new ParameterizedTypeReference<List<TestOrder>>() {});
    List<Object> untyped = queryBus.ask(new TestOrderListQuery(true));

    assertThat(lastReplyPayloadType).startsWith("java.util.ImmutableCollections$");
    assertThat(typed).containsExactly(new TestOrder("o-1"), new TestOrder("o-2"));
    assertThat(untyped).containsExactly(Map.of("id", "o-1"), Map.of("id", "o-2"));
  }

  @Test
  void dispatchAndReceiveReturnsTheTypedWrapperWithATypeReference() {
    TestResult<TestOrder> result =
        commandBus.dispatchAndReceive(
            new TestOrderResultCommand("o-7"),
            new ParameterizedTypeReference<TestResult<TestOrder>>() {});

    assertThat(result.value()).isEqualTo(new TestOrder("o-7"));
  }

  @Test
  void dispatchAndReceiveReturnsTheWrapperWithMapContentWithoutATypeReference() {
    TestResult<?> result = commandBus.dispatchAndReceive(new TestOrderResultCommand("o-7"));

    assertThat(result.value()).isEqualTo(Map.of("id", "o-7"));
  }

  @Test
  void aResultDescribedByItsRuntimeClassNeedsNoTypeReference() {
    TestOrder order = commandBus.dispatchAndReceive(new TestOrderResultCommand("single"));

    assertThat(order).isEqualTo(new TestOrder("single"));
  }

  private QueryHandlerRegistry queryRegistry() {
    QueryHandlerRegistry registry = mock(QueryHandlerRegistry.class);
    when(registry.getHandlerInfo(TestOrderListQuery.class))
        .thenReturn(Optional.of(mock(QueryHandlerRegistry.HandlerInfo.class)));
    when(registry.handle(any()))
        .thenAnswer(
            invocation -> {
              TestOrderListQuery query = invocation.getArgument(0);
              return query.isStreamed()
                  ? Stream.of(new TestOrder("o-1"), new TestOrder("o-2")).toList()
                  : List.of(new TestOrder("o-1"), new TestOrder("o-2"));
            });
    return registry;
  }

  private CommandHandlerRegistry commandRegistry() {
    CommandHandlerRegistry registry = mock(CommandHandlerRegistry.class);
    when(registry.getHandlerInfo(TestOrderResultCommand.class))
        .thenReturn(Optional.of(mock(CommandHandlerRegistry.HandlerInfo.class)));
    when(registry.handle(any()))
        .thenAnswer(
            invocation -> {
              TestOrderResultCommand command = invocation.getArgument(0);
              TestOrder order = new TestOrder(command.getOrderId());
              return "single".equals(command.getOrderId()) ? order : new TestResult<>(order);
            });
    return registry;
  }

  /** Stands in for the broker: hands the record to whoever reads its topic. */
  private void deliver(ProducerRecord<String, byte[]> sent) {
    ConsumerRecord<String, byte[]> received =
        new ConsumerRecord<>(sent.topic(), 0, 0L, sent.key(), sent.value());
    sent.headers().forEach(header -> received.headers().add(header));
    if (REPLY_TOPIC.equals(sent.topic())) {
      lastReplyPayloadType =
          new String(sent.headers().lastHeader(KafkaMessageHeaders.PAYLOAD_TYPE).value(), UTF_8);
      client.handleReply(received);
    } else if (topicNaming.topic("queries").equals(sent.topic())) {
      queryConsumer.consume(received);
    } else {
      commandConsumer.consume(received);
    }
  }
}
