package com.borjaglez.cqrs.rabbitmq.config;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

import com.borjaglez.cqrs.rabbitmq.aot.RabbitMqCqrsRuntimeHints;
import com.borjaglez.cqrs.rabbitmq.infrastructure.DefaultRabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

@AutoConfiguration
@AutoConfigureAfter(name = "com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(
    prefix = "cqrs.rabbitmq",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(RabbitMqCqrsProperties.class)
@ImportRuntimeHints(RabbitMqCqrsRuntimeHints.class)
public class RabbitMqCqrsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Jackson2JsonMessageConverter cqrsMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  public RabbitMqNamingStrategy rabbitMqNamingStrategy(RabbitMqCqrsProperties properties) {
    return new DefaultRabbitMqNamingStrategy(properties.getPrefix());
  }

  @Bean
  @ConditionalOnMissingBean
  public RabbitMqPublisher cqrsRabbitMqPublisher(RabbitTemplate rabbitTemplate) {
    return new RabbitMqPublisher(rabbitTemplate);
  }

  @Bean
  @ConditionalOnMissingBean
  public RabbitMqBusDeclarationBuilder rabbitMqBusDeclarationBuilder(
      RabbitMqNamingStrategy namingStrategy) {
    return new RabbitMqBusDeclarationBuilder(namingStrategy);
  }
}
