package com.borjaglez.cqrs.kafka.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.serialization.MessageSerializer;

public class KafkaEventConsumer extends AbstractKafkaConsumer {

  private final EventHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;

  public KafkaEventConsumer(
      EventHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      MessageSerializer serializer) {
    this(registry, middlewares, serializer, KafkaMessagePublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public KafkaEventConsumer(
      EventHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      MessageSerializer serializer,
      String contextHeaderPrefix) {
    super(serializer, contextHeaderPrefix);
    this.registry = registry;
    this.middlewares = middlewares;
  }

  public void consume(ConsumerRecord<String, byte[]> record) {
    Event event = deserialize(record);
    MessageContext incoming = extractContext(record);
    MessageContext.Scope scope = MessageContext.scope(incoming);
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(
              middlewares,
              message -> {
                registry.handle((Event) message);
                return null;
              });
      chain.proceed(event);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      scope.close();
    }
  }
}
