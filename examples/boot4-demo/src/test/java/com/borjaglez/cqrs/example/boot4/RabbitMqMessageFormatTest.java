package com.borjaglez.cqrs.example.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.jackson.property-naming-strategy=SNAKE_CASE")
class RabbitMqMessageFormatTest {

  @Autowired
  @Qualifier("cqrsMessageConverter")
  private MessageConverter messageConverter;

  @Test
  void messagesAreWrittenWithTheApplicationJsonMapper() {
    OffsetDateTime sentAt = OffsetDateTime.of(2026, 9, 30, 10, 15, 30, 123_456_000, ZoneOffset.UTC);

    byte[] body =
        messageConverter.toMessage(new TimedReply(sentAt), new MessageProperties()).getBody();

    assertThat(new String(body, StandardCharsets.UTF_8))
        .contains("\"sent_at\":\"2026-09-30T10:15:30.123456Z\"");
  }

  record TimedReply(OffsetDateTime sentAt) {}
}
