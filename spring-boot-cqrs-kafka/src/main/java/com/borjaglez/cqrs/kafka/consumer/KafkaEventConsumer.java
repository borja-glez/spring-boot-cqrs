package com.borjaglez.cqrs.kafka.consumer;

import java.util.List;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;
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
    this.middlewares = DispatchPhase.INBOUND.select(middlewares);
  }

  public void consume(ConsumerRecord<String, byte[]> record) {
    // An event type this application does not have cannot have a handler here either.
    Optional<Class<?>> type = localPayloadClass(record);
    if (type.isEmpty()) {
      return;
    }
    Event event = (Event) deserialize(record, type.get());
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
