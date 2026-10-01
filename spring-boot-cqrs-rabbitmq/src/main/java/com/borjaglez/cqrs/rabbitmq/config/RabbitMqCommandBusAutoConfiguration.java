package com.borjaglez.cqrs.rabbitmq.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqCommandConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.MessageNameResolvingConverter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitTemplateFactory;

@AutoConfiguration
@AutoConfigureAfter(RabbitMqCqrsAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(CommandHandlerRegistry.class)
@ConditionalOnProperty(
    prefix = "cqrs.rabbitmq",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBooleanProperty(name = "cqrs.rabbitmq.commands.enabled", matchIfMissing = true)
public class RabbitMqCommandBusAutoConfiguration {

  /** Name of the command bus's own {@code RabbitTemplate}, used when a reply timeout is set. */
  public static final String COMMAND_RABBIT_TEMPLATE = "cqrsCommandRabbitTemplate";

  /**
   * Name of the command bus's own {@code RabbitMqPublisher}. When a bean with this name exists, the
   * command bus sends through it instead of the shared publisher.
   */
  public static final String COMMAND_PUBLISHER = "cqrsCommandRabbitMqPublisher";

  @Bean
  public Declarables cqrsCommandDeclarables(
      RabbitMqBusDeclarationBuilder builder,
      RabbitMqCqrsProperties properties,
      CommandHandlerRegistry registry,
      MessageNamingStrategy messageNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    List<String> routingKeys =
        new ArrayList<>(
            registry.getRegisteredCommands().stream()
                .filter(type -> properties.getExpose().exposesCommand(registry, type))
                .map(messageNaming::commandName)
                .toList());
    return builder.buildWithRetryAndDeadLetter(
        appName,
        properties.getCommands().getExchange(),
        routingKeys,
        properties.getRetry().getTtl());
  }

  /**
   * The command bus's own template when {@code cqrs.rabbitmq.commands.reply-timeout} is set. It is
   * not a default candidate, so it never replaces the application's {@code RabbitTemplate} where
   * one is injected by type.
   */
  @Bean(name = COMMAND_RABBIT_TEMPLATE, defaultCandidate = false)
  @ConditionalOnProperty(prefix = "cqrs.rabbitmq.commands", name = "reply-timeout")
  @ConditionalOnMissingBean(name = COMMAND_RABBIT_TEMPLATE)
  public RabbitTemplate cqrsCommandRabbitTemplate(
      BeanFactory beanFactory,
      ConnectionFactory connectionFactory,
      RabbitMqCqrsProperties properties) {
    return RabbitTemplateFactory.create(
        beanFactory, connectionFactory, properties.getCommands().getReplyTimeout());
  }

  /** The command bus's own publisher, over {@link #cqrsCommandRabbitTemplate}. */
  @Bean(name = COMMAND_PUBLISHER, defaultCandidate = false)
  @ConditionalOnProperty(prefix = "cqrs.rabbitmq.commands", name = "reply-timeout")
  @ConditionalOnMissingBean(name = COMMAND_PUBLISHER)
  public RabbitMqPublisher cqrsCommandRabbitMqPublisher(
      @Qualifier(COMMAND_RABBIT_TEMPLATE) RabbitTemplate rabbitTemplate,
      @Value("${cqrs.context.header-prefix:cqrs.context.}") String contextHeaderPrefix) {
    return new RabbitMqPublisher(rabbitTemplate, contextHeaderPrefix);
  }

  @Bean
  public RabbitMqCommandBus rabbitMqCommandBus(
      RabbitMqPublisher publisher,
      @Qualifier(COMMAND_PUBLISHER) ObjectProvider<RabbitMqPublisher> commandPublisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      RabbitMqCqrsProperties properties,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider) {
    // The bus keeps the middlewares that declare DispatchPhase.OUTBOUND.
    return new RabbitMqCommandBus(
        commandPublisher.getIfAvailable(() -> publisher),
        rabbitNaming,
        messageNaming,
        properties.getCommands().getExchange(),
        middlewaresProvider.getIfAvailable(Collections::emptyList));
  }

  @Bean
  public SimpleMessageListenerContainer cqrsCommandListenerContainer(
      ConnectionFactory connectionFactory,
      RabbitMqCqrsProperties properties,
      CommandHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      @Qualifier("cqrsMessageConverter") MessageConverter messageConverter,
      RabbitMqNamingStrategy rabbitNaming,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider,
      @Value("${spring.application.name:cqrs-app}") String appName,
      @Value("${cqrs.context.header-prefix:cqrs.context.}") String contextHeaderPrefix,
      @Value("${spring.rabbitmq.listener.simple.observation-enabled:false}")
          boolean observationEnabled) {
    RabbitMqCommandConsumer consumer =
        new RabbitMqCommandConsumer(
            registry,
            middlewaresProvider.getIfAvailable(Collections::emptyList),
            rabbitTemplate,
            rabbitNaming,
            properties.getCommands().getExchange(),
            appName,
            contextHeaderPrefix,
            properties.getRetry().getMaxAttempts(),
            properties.getExpose());

    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(
            consumer,
            new MessageNameResolvingConverter(messageConverter, registry::findMessageClass),
            "consume");

    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setMessageListener(adapter);
    container.setQueueNames(rabbitNaming.queue(appName, properties.getCommands().getExchange()));
    container.setConcurrentConsumers(properties.getCommands().getConcurrentConsumers());
    container.setMaxConcurrentConsumers(properties.getCommands().getMaxConcurrentConsumers());
    // Same switch as Boot's listener containers: with it the trace of the sender continues here.
    container.setObservationEnabled(observationEnabled);
    return container;
  }
}
