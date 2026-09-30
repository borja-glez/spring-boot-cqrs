package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;

class MessageNameResolvingConverterTest {

  private static final String NAME = "test.1.event.order.created";

  private final MessageConverter json = JsonMessageConverterFactory.create();

  private final Function<String, Optional<Class<?>>> classByName =
      name -> NAME.equals(name) ? Optional.of(TestEvent.class) : Optional.empty();

  private Message messageFromARenamedProducer(Object nameHeader) {
    Message message = json.toMessage(new TestEvent("data"), new MessageProperties());
    message.getMessageProperties().setHeader("__TypeId__", "com.example.producer.OrderCreated");
    if (nameHeader != null) {
      message.getMessageProperties().setHeader(RabbitMqPublisher.HEADER_MESSAGE_NAME, nameHeader);
    }
    return message;
  }

  @Test
  void readsTheMessageAsTheClassRegisteredUnderItsName() {
    MessageNameResolvingConverter converter = new MessageNameResolvingConverter(json, classByName);

    Object converted = converter.fromMessage(messageFromARenamedProducer(NAME));

    assertThat(converted).isInstanceOf(TestEvent.class);
    assertThat(((TestEvent) converted).getData()).isEqualTo("data");
  }

  @Test
  void fallsBackToTheDelegateWithoutMessageNameHeader() {
    MessageConverter delegate = mock(MessageConverter.class);
    Message message = messageFromARenamedProducer(null);
    when(delegate.fromMessage(message)).thenReturn("from-type-id");

    Object converted =
        new MessageNameResolvingConverter(delegate, classByName).fromMessage(message);

    assertThat(converted).isEqualTo("from-type-id");
  }

  @Test
  void fallsBackToTheDelegateForAnUnknownName() {
    Message message = json.toMessage(new TestEvent("data"), new MessageProperties());
    message.getMessageProperties().setHeader(RabbitMqPublisher.HEADER_MESSAGE_NAME, "unknown");

    Object converted = new MessageNameResolvingConverter(json, classByName).fromMessage(message);

    // Read through __TypeId__, which names the real TestEvent class here.
    assertThat(converted).isInstanceOf(TestEvent.class);
  }

  @Test
  void fallsBackToTheDelegateWhenTheNameHeaderIsNotAString() {
    MessageConverter delegate = mock(MessageConverter.class);
    Message message = messageFromARenamedProducer(42);
    when(delegate.fromMessage(message)).thenReturn("from-type-id");

    assertThat(new MessageNameResolvingConverter(delegate, classByName).fromMessage(message))
        .isEqualTo("from-type-id");
  }

  @Test
  void fallsBackToTheDelegateWhenItIsNotSmart() {
    MessageConverter delegate = mock(MessageConverter.class);
    Message message = messageFromARenamedProducer(NAME);
    when(delegate.fromMessage(message)).thenReturn("plain");

    assertThat(new MessageNameResolvingConverter(delegate, classByName).fromMessage(message))
        .isEqualTo("plain");
  }

  @Test
  void delegatesToMessage() {
    MessageConverter delegate = mock(MessageConverter.class);
    MessageProperties properties = new MessageProperties();
    Message expected = new Message(new byte[0], properties);
    when(delegate.toMessage("payload", properties)).thenReturn(expected);

    assertThat(
            new MessageNameResolvingConverter(delegate, classByName)
                .toMessage("payload", properties))
        .isSameAs(expected);
  }

  @Test
  void delegatesGenericToMessage() {
    MessageConverter delegate = mock(MessageConverter.class);
    MessageProperties properties = new MessageProperties();
    Type listOfMaps = new ParameterizedTypeReference<List<Map<String, String>>>() {}.getType();
    Message expected = new Message(new byte[0], properties);
    when(delegate.toMessage(List.of(), properties, listOfMaps)).thenReturn(expected);

    assertThat(
            new MessageNameResolvingConverter(delegate, classByName)
                .toMessage(List.of(), properties, listOfMaps))
        .isSameAs(expected);
  }
}
