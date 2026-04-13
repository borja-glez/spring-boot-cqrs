package com.borjaglez.cqrs.kafka.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.serialization.MessageSerializer;

public class KafkaQueryConsumer extends AbstractKafkaConsumer {

  private final QueryHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;
  private final KafkaMessagePublisher publisher;

  public KafkaQueryConsumer(
      QueryHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      MessageSerializer serializer,
      KafkaMessagePublisher publisher) {
    this(
        registry,
        middlewares,
        serializer,
        publisher,
        KafkaMessagePublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public KafkaQueryConsumer(
      QueryHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      MessageSerializer serializer,
      KafkaMessagePublisher publisher,
      String contextHeaderPrefix) {
    super(serializer, contextHeaderPrefix);
    this.registry = registry;
    this.middlewares = middlewares;
    this.publisher = publisher;
  }

  public void consume(ConsumerRecord<String, byte[]> record) {
    Query query = deserialize(record);
    String replyTopic = header(record, KafkaMessageHeaders.REPLY_TOPIC);
    String correlationId = header(record, KafkaMessageHeaders.CORRELATION_ID);
    MessageContext incoming = extractContext(record);
    MessageContext.Scope scope = MessageContext.scope(incoming);

    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(middlewares, message -> registry.handle((Query) message));
      Object result = chain.proceed(query);
      publisher.publishReply(replyTopic, correlationId, result);
    } catch (RuntimeException e) {
      publisher.publishErrorReply(replyTopic, correlationId, e);
    } catch (Exception e) {
      publisher.publishErrorReply(replyTopic, correlationId, new RuntimeException(e));
    } finally {
      scope.close();
    }
  }
}
