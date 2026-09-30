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

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqQueryConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.MessageNameResolvingConverter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitTemplateFactory;

@AutoConfiguration
@AutoConfigureAfter(RabbitMqCqrsAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(QueryHandlerRegistry.class)
@ConditionalOnProperty(
    prefix = "cqrs.rabbitmq",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBooleanProperty(name = "cqrs.rabbitmq.queries.enabled", matchIfMissing = true)
public class RabbitMqQueryBusAutoConfiguration {

  /** Name of the query bus's own {@code RabbitTemplate}, used when a reply timeout is set. */
  public static final String QUERY_RABBIT_TEMPLATE = "cqrsQueryRabbitTemplate";

  /**
   * Name of the query bus's own {@code RabbitMqPublisher}. When a bean with this name exists, the
   * query bus sends through it instead of the shared publisher.
   */
  public static final String QUERY_PUBLISHER = "cqrsQueryRabbitMqPublisher";

  @Bean
  public Declarables cqrsQueryDeclarables(
      RabbitMqBusDeclarationBuilder builder,
      RabbitMqCqrsProperties properties,
      QueryHandlerRegistry registry,
      MessageNamingStrategy messageNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    List<String> routingKeys =
        new ArrayList<>(
            registry.getRegisteredQueries().stream()
                .filter(type -> properties.getExpose().exposesQuery(registry, type))
                .map(messageNaming::queryName)
                .toList());
    return builder.buildSimple(appName, properties.getQueries().getExchange(), routingKeys);
  }

  /**
   * The query bus's own template when {@code cqrs.rabbitmq.queries.reply-timeout} is set. It is not
   * a default candidate, so it never replaces the application's {@code RabbitTemplate} where one is
   * injected by type.
   */
  @Bean(name = QUERY_RABBIT_TEMPLATE, defaultCandidate = false)
  @ConditionalOnProperty(prefix = "cqrs.rabbitmq.queries", name = "reply-timeout")
  @ConditionalOnMissingBean(name = QUERY_RABBIT_TEMPLATE)
  public RabbitTemplate cqrsQueryRabbitTemplate(
      BeanFactory beanFactory,
      ConnectionFactory connectionFactory,
      RabbitMqCqrsProperties properties) {
    return RabbitTemplateFactory.create(
        beanFactory, connectionFactory, properties.getQueries().getReplyTimeout());
  }

  /** The query bus's own publisher, over {@link #cqrsQueryRabbitTemplate}. */
  @Bean(name = QUERY_PUBLISHER, defaultCandidate = false)
  @ConditionalOnProperty(prefix = "cqrs.rabbitmq.queries", name = "reply-timeout")
  @ConditionalOnMissingBean(name = QUERY_PUBLISHER)
  public RabbitMqPublisher cqrsQueryRabbitMqPublisher(
      @Qualifier(QUERY_RABBIT_TEMPLATE) RabbitTemplate rabbitTemplate,
      @Value("${cqrs.context.header-prefix:cqrs.context.}") String contextHeaderPrefix) {
    return new RabbitMqPublisher(rabbitTemplate, contextHeaderPrefix);
  }

  @Bean
  public RabbitMqQueryBus rabbitMqQueryBus(
      RabbitMqPublisher publisher,
      @Qualifier(QUERY_PUBLISHER) ObjectProvider<RabbitMqPublisher> queryPublisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      RabbitMqCqrsProperties properties,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider) {
    // The bus keeps the middlewares that declare DispatchPhase.OUTBOUND.
    return new RabbitMqQueryBus(
        queryPublisher.getIfAvailable(() -> publisher),
        rabbitNaming,
        messageNaming,
        properties.getQueries().getExchange(),
        middlewaresProvider.getIfAvailable(Collections::emptyList));
  }

  @Bean
  public SimpleMessageListenerContainer cqrsQueryListenerContainer(
      ConnectionFactory connectionFactory,
      RabbitMqCqrsProperties properties,
      QueryHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      @Qualifier("cqrsMessageConverter") MessageConverter messageConverter,
      RabbitMqNamingStrategy rabbitNaming,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider,
      @Value("${spring.application.name:cqrs-app}") String appName,
      @Value("${cqrs.context.header-prefix:cqrs.context.}") String contextHeaderPrefix,
      @Value("${spring.rabbitmq.listener.simple.observation-enabled:false}")
          boolean observationEnabled) {
    RabbitMqQueryConsumer consumer =
        new RabbitMqQueryConsumer(
            registry,
            middlewaresProvider.getIfAvailable(Collections::emptyList),
            rabbitTemplate,
            rabbitNaming,
            contextHeaderPrefix,
            properties.getExpose());

    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(
            consumer,
            new MessageNameResolvingConverter(messageConverter, registry::findMessageClass),
            "consume");

    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setMessageListener(adapter);
    container.setQueueNames(rabbitNaming.queue(appName, properties.getQueries().getExchange()));
    container.setConcurrentConsumers(properties.getQueries().getConcurrentConsumers());
    container.setMaxConcurrentConsumers(properties.getQueries().getMaxConcurrentConsumers());
    // Same switch as Boot's listener containers: with it the trace of the sender continues here.
    container.setObservationEnabled(observationEnabled);
    return container;
  }
}
