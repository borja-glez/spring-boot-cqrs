package com.borjaglez.cqrs.example.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;

/** The reply timeout per bus on Spring Boot 4, whose RabbitMQ types moved to another package. */
@SpringBootTest(
    properties = {
      "cqrs.rabbitmq.commands.reply-timeout=5s",
      "cqrs.rabbitmq.queries.reply-timeout=1s",
      "spring.rabbitmq.template.reply-timeout=3s",
      "spring.rabbitmq.template.mandatory=true",
      "spring.rabbitmq.template.observation-enabled=true",
      "spring.rabbitmq.template.retry.enabled=true"
    })
class RabbitMqReplyTimeoutTest {

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired
  @Qualifier("cqrsCommandRabbitTemplate")
  private RabbitTemplate commandTemplate;

  @Autowired
  @Qualifier("cqrsQueryRabbitTemplate")
  private RabbitTemplate queryTemplate;

  @Autowired private RabbitMqCommandBus commandBus;

  @Autowired private RabbitMqQueryBus queryBus;

  @Autowired private ObjectProvider<RabbitTemplate> rabbitTemplates;

  @Test
  void eachBusSendsThroughItsOwnTemplateWithItsReplyTimeout() {
    assertThat(replyTimeout(rabbitTemplate)).isEqualTo(3000L);
    assertThat(replyTimeout(commandTemplate)).isEqualTo(5000L);
    assertThat(replyTimeout(queryTemplate)).isEqualTo(1000L);
    assertThat(commandBus)
        .extracting("publisher")
        .extracting("rabbitTemplate")
        .isSameAs(commandTemplate);
    assertThat(queryBus)
        .extracting("publisher")
        .extracting("rabbitTemplate")
        .isSameAs(queryTemplate);
    // The application's template is still the one injected by type.
    assertThat(rabbitTemplates.getIfUnique()).isSameAs(rabbitTemplate);
  }

  @Test
  void theTemplatesOfTheBusesKeepBootsSettingsAndCustomizers() {
    for (RabbitTemplate template : new RabbitTemplate[] {commandTemplate, queryTemplate}) {
      assertThat(template).isNotSameAs(rabbitTemplate);
      assertThat(template.isMandatoryFor(null)).isTrue();
      assertThat(ReflectionTestUtils.getField(template, "observationEnabled")).isEqualTo(true);
      assertThat(ReflectionTestUtils.getField(template, "retryTemplate")).isNotNull();
      assertThat(template.getMessageConverter()).isSameAs(rabbitTemplate.getMessageConverter());
      assertThat(template.getConnectionFactory()).isSameAs(rabbitTemplate.getConnectionFactory());
      assertThat(template.getRoutingKey()).isEqualTo("customized");
    }
  }

  private static Object replyTimeout(RabbitTemplate template) {
    return ReflectionTestUtils.getField(template, "replyTimeout");
  }

  @TestConfiguration
  static class CustomizerConfiguration {

    @Bean
    RabbitTemplateCustomizer routingKeyCustomizer() {
      return template -> template.setRoutingKey("customized");
    }
  }
}
