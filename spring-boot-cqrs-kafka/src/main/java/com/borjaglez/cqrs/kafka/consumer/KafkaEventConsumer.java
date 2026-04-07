package com.borjaglez.cqrs.kafka.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.serialization.MessageSerializer;

public class KafkaEventConsumer extends AbstractKafkaConsumer {

  private final EventHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;

  public KafkaEventConsumer(
      EventHandlerRegistry registry, List<BusMiddleware> middlewares, MessageSerializer serializer) {
    super(serializer);
    this.registry = registry;
    this.middlewares = middlewares;
  }

  public void consume(ConsumerRecord<String, byte[]> record) {
    Event event = deserialize(record);
    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(
            middlewares,
            message -> {
              registry.handle((Event) message);
              return null;
            });
    try {
      chain.proceed(event);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
