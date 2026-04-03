package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

class RabbitMqPublisherTest {

  private RabbitTemplate rabbitTemplate;
  private RabbitMqPublisher publisher;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    publisher = new RabbitMqPublisher(rabbitTemplate);
  }

  @Test
  void publishShouldSendMessageWithCorrectHeaders() {
    Object payload = "test-payload";

    publisher.publish("my-exchange", "routing.key", payload, "command");

    ArgumentCaptor<MessagePostProcessor> postProcessorCaptor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("my-exchange"), eq("routing.key"), eq(payload), postProcessorCaptor.capture());

    // Verify the post processor sets the header
    MessagePostProcessor postProcessor = postProcessorCaptor.getValue();
    Message testMessage =
        MessageBuilder.withBody("test".getBytes()).andProperties(new MessageProperties()).build();
    Message processedMessage = postProcessor.postProcessMessage(testMessage);
    Object headerValue = processedMessage.getMessageProperties().getHeader("cqrs.message.type");
    assertThat(headerValue).isEqualTo("command");
  }

  @Test
  void publishAndReceiveShouldReturnDeserializedResult() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    Message replyMessage =
        MessageBuilder.withBody("reply".getBytes()).andProperties(replyProps).build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any())).thenReturn(replyMessage);

    String expectedResult = "deserialized-result";
    when(converter.fromMessage(replyMessage)).thenReturn(expectedResult);

    Object result = publisher.publishAndReceive("exchange", "key", "payload", "command_reply");

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void publishAndReceiveShouldReturnNullWhenNoReply() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any(Message.class)))
        .thenReturn(null);

    Object result = publisher.publishAndReceive("exchange", "key", "payload", "query");

    assertThat(result).isNull();
  }

  @Test
  void publishAndReceiveShouldThrowOnErrorReply() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    replyProps.setHeader("cqrs.error", true);
    Message errorReply =
        MessageBuilder.withBody("Something went wrong".getBytes())
            .andProperties(replyProps)
            .build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any(Message.class)))
        .thenReturn(errorReply);

    assertThatThrownBy(
            () -> publisher.publishAndReceive("exchange", "key", "payload", "command_reply"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Remote handler error: Something went wrong");
  }

  @Test
  void checkErrorShouldThrowWhenErrorHeaderIsTrue() {
    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.error", true);
    Message message =
        MessageBuilder.withBody("error details".getBytes()).andProperties(props).build();

    assertThatThrownBy(() -> publisher.checkError(message))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Remote handler error: error details");
  }

  @Test
  void checkErrorShouldNotThrowWhenErrorHeaderIsFalse() {
    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.error", false);
    Message message = MessageBuilder.withBody("data".getBytes()).andProperties(props).build();

    publisher.checkError(message); // should not throw
  }

  @Test
  void checkErrorShouldNotThrowWhenErrorHeaderIsMissing() {
    MessageProperties props = new MessageProperties();
    Message message = MessageBuilder.withBody("data".getBytes()).andProperties(props).build();

    publisher.checkError(message); // should not throw
  }
}
