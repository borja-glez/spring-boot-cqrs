package com.borjaglez.cqrs.kafka.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.serialization.MessageSerializer;

public class KafkaCommandConsumer extends AbstractKafkaConsumer {

  private final CommandHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;
  private final KafkaMessagePublisher publisher;

  public KafkaCommandConsumer(
      CommandHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      MessageSerializer serializer,
      KafkaMessagePublisher publisher) {
    super(serializer);
    this.registry = registry;
    this.middlewares = middlewares;
    this.publisher = publisher;
  }

  public void consume(ConsumerRecord<String, byte[]> record) {
    Command command = deserialize(record);
    KafkaRequestMode requestMode = requestMode(record);
    String replyTopic = header(record, KafkaMessageHeaders.REPLY_TOPIC);
    String correlationId = header(record, KafkaMessageHeaders.CORRELATION_ID);

    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(middlewares, message -> registry.handle((Command) message));
      Object result = chain.proceed(command);
      if (requestMode == KafkaRequestMode.REPLY && replyTopic != null && correlationId != null) {
        publisher.publishReply(replyTopic, correlationId, result);
      }
      if (requestMode == KafkaRequestMode.WAIT && replyTopic != null && correlationId != null) {
        publisher.publishReply(replyTopic, correlationId, "");
      }
    } catch (RuntimeException e) {
      if (requestMode != null && replyTopic != null && correlationId != null) {
        publisher.publishErrorReply(replyTopic, correlationId, e);
        return;
      }
      throw e;
    } catch (Exception e) {
      RuntimeException runtimeException = new RuntimeException(e);
      if (requestMode != null && replyTopic != null && correlationId != null) {
        publisher.publishErrorReply(replyTopic, correlationId, runtimeException);
        return;
      }
      throw runtimeException;
    }
  }

  private KafkaRequestMode requestMode(ConsumerRecord<String, byte[]> record) {
    String raw = header(record, KafkaMessageHeaders.REQUEST_MODE);
    return raw == null ? null : KafkaRequestMode.valueOf(raw);
  }
}
