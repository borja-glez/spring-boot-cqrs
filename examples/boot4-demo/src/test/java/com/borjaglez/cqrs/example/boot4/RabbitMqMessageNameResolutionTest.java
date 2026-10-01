package com.borjaglez.cqrs.example.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.cqrs.example.boot4.event.OrderCreatedEvent;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;

/**
 * The event listener reads an event by its {@code @CqrsMessage} name with the Jackson 3 converter
 * of Spring AMQP 4, whatever class the producer named in {@code __TypeId__}.
 */
@SpringBootTest
class RabbitMqMessageNameResolutionTest {

  @Autowired
  @Qualifier("cqrsMessageConverter")
  private MessageConverter messageConverter;

  @Autowired
  @Qualifier("cqrsEventListenerContainer")
  private SimpleMessageListenerContainer eventListenerContainer;

  @Autowired private MessageNamingStrategy messageNamingStrategy;

  @Test
  void eventFromARenamedProducerClassIsReadAsTheLocalClass() {
    Message message =
        messageConverter.toMessage(
            new OrderCreatedEvent("order-1", "book", 2), new MessageProperties());
    message
        .getMessageProperties()
        .setHeader("__TypeId__", "com.example.producer.orders.OrderPlaced");
    message
        .getMessageProperties()
        .setHeader("cqrs.message.name", messageNamingStrategy.eventName(OrderCreatedEvent.class));
    MessageConverter consumerConverter =
        (MessageConverter)
            ReflectionTestUtils.getField(
                eventListenerContainer.getMessageListener(), "messageConverter");

    Object event = consumerConverter.fromMessage(message);

    assertThat(event).isInstanceOf(OrderCreatedEvent.class);
    assertThat(((OrderCreatedEvent) event).getOrderId()).isEqualTo("order-1");
    assertThat(((OrderCreatedEvent) event).getQuantity()).isEqualTo(2);
  }
}
