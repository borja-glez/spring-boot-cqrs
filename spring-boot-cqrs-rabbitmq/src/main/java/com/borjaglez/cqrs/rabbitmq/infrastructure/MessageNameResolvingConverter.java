package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Reads an incoming CQRS message as the local class registered under the logical name in its
 * {@value RabbitMqPublisher#HEADER_MESSAGE_NAME} header, instead of the class the producer named in
 * Spring AMQP's {@code __TypeId__} header. The producer may therefore rename or move the class
 * without breaking this consumer.
 *
 * <p>A message without that header (sent by an older producer), with a name this application does
 * not handle, or read by a delegate that is not a {@link SmartMessageConverter} is converted by the
 * delegate as before. Writing is always left to the delegate.
 */
public class MessageNameResolvingConverter implements MessageConverter {

  private final MessageConverter delegate;
  private final Function<String, Optional<Class<?>>> classByName;

  /**
   * Creates the converter.
   *
   * @param delegate the converter that reads and writes the JSON
   * @param classByName the local class handled under a logical name, usually a handler registry's
   *     {@code findMessageClass}
   */
  public MessageNameResolvingConverter(
      MessageConverter delegate, Function<String, Optional<Class<?>>> classByName) {
    this.delegate = delegate;
    this.classByName = classByName;
  }

  @Override
  public Message toMessage(Object object, MessageProperties messageProperties) {
    return delegate.toMessage(object, messageProperties);
  }

  @Override
  public Message toMessage(Object object, MessageProperties messageProperties, Type genericType) {
    return delegate.toMessage(object, messageProperties, genericType);
  }

  @Override
  public Object fromMessage(Message message) {
    if (delegate instanceof SmartMessageConverter smartConverter
        && message.getMessageProperties().getHeader(RabbitMqPublisher.HEADER_MESSAGE_NAME)
            instanceof String messageName) {
      Optional<Class<?>> localClass = classByName.apply(messageName);
      if (localClass.isPresent()) {
        return smartConverter.fromMessage(
            message, ParameterizedTypeReference.forType(localClass.get()));
      }
    }
    return delegate.fromMessage(message);
  }
}
