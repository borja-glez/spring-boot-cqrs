package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalQueryHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.OutboundBlocker;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestOrderListQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestOrderListQueryHandler;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQueryHandler;
import com.borjaglez.cqrs.rabbitmq.infrastructure.MessageNameResolvingConverter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqExposure;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

class RabbitMqQueryBusAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RabbitAutoConfiguration.class,
                  RabbitMqCqrsAutoConfiguration.class,
                  RabbitMqQueryBusAutoConfiguration.class))
          .withBean(QueryHandlerRegistry.class, QueryHandlerRegistry::new)
          .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"));

  @Test
  void shouldCreateQueryBusAndDeclarables() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RabbitMqQueryBus.class);
          assertThat(context).hasBean("cqrsQueryDeclarables");
        });
  }

  @Test
  void shouldNotCreateBeansWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqQueryBus.class);
            });
  }

  @Test
  void shouldNotCreateBeansWithoutQueryHandlerRegistry() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RabbitAutoConfiguration.class,
                RabbitMqCqrsAutoConfiguration.class,
                RabbitMqQueryBusAutoConfiguration.class))
        .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqQueryBus.class);
            });
  }

  @Test
  void shouldObserveTheListenerContainerLikeBootDoes() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(
                        "cqrsQueryListenerContainer", SimpleMessageListenerContainer.class))
                .hasFieldOrPropertyWithValue("observationEnabled", false));
    contextRunner
        .withPropertyValues("spring.rabbitmq.listener.simple.observation-enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "cqrsQueryListenerContainer", SimpleMessageListenerContainer.class))
                    .hasFieldOrPropertyWithValue("observationEnabled", true));
  }

  @Test
  void shouldNotCreateQueryBusBeansWhenQueriesAreDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.queries.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqQueryBus.class);
              assertThat(context).doesNotHaveBean("cqrsQueryDeclarables");
              assertThat(context).doesNotHaveBean("cqrsQueryListenerContainer");
              assertThat(context).hasSingleBean(RabbitMqPublisher.class);
              assertThat(context).hasSingleBean(RabbitMqBusDeclarationBuilder.class);
            });
  }

  @Test
  void shouldCreateQueryBusBeansWhenQueriesAreExplicitlyEnabledAndOtherBusesAreDisabled() {
    contextRunner
        .withPropertyValues(
            "cqrs.rabbitmq.queries.enabled=true",
            "cqrs.rabbitmq.commands.enabled=false",
            "cqrs.rabbitmq.events.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RabbitMqQueryBus.class);
              assertThat(context).hasBean("cqrsQueryDeclarables");
              assertThat(context).hasBean("cqrsQueryListenerContainer");
            });
  }

  @Test
  void shouldNotCreateQueryBusBeansWhenRabbitMqIsDisabledEvenIfQueriesAreEnabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false", "cqrs.rabbitmq.queries.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqQueryBus.class);
              assertThat(context).doesNotHaveBean("cqrsQueryDeclarables");
              assertThat(context).doesNotHaveBean("cqrsQueryListenerContainer");
            });
  }

  private static QueryHandlerRegistry registryWithLocalMessages() {
    try {
      QueryHandlerRegistry registry = new QueryHandlerRegistry();
      registry.register(
          TestQuery.class,
          new TestQueryHandler(),
          TestQueryHandler.class.getMethod("handle", TestQuery.class),
          "get");
      registry.register(
          LocalQuery.class,
          new LocalQueryHandler(),
          LocalQueryHandler.class.getMethod("handle", LocalQuery.class),
          "local");
      registry.register(
          TestOrderListQuery.class,
          new TestOrderListQueryHandler(),
          TestOrderListQueryHandler.class.getMethod("handle", TestOrderListQuery.class),
          "list",
          false);
      return registry;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<String> messageBindings(Declarables declarables, String exchange) {
    return declarables.getDeclarablesByType(Binding.class).stream()
        .filter(b -> b.getExchange().equals(exchange))
        .map(Binding::getRoutingKey)
        .toList();
  }

  @Test
  void shouldBindOnlyAnnotatedRemoteMessagesByDefault() {
    contextRunner
        .withAllowBeanDefinitionOverriding(true)
        .withBean(
            QueryHandlerRegistry.class,
            RabbitMqQueryBusAutoConfigurationTest::registryWithLocalMessages)
        .run(
            context -> {
              Declarables declarables = context.getBean("cqrsQueryDeclarables", Declarables.class);
              MessageNamingStrategy naming = context.getBean(MessageNamingStrategy.class);
              assertThat(messageBindings(declarables, "cqrs.queries"))
                  .containsExactly(naming.queryName(TestQuery.class));
              assertThat(
                      context
                          .getBean(
                              "cqrsQueryListenerContainer", SimpleMessageListenerContainer.class)
                          .getMessageListener())
                  .extracting("delegate")
                  .extracting("exposure")
                  .isEqualTo(RabbitMqExposure.ANNOTATED);
            });
  }

  @Test
  void shouldBindUnannotatedMessagesButNotLocalHandlersWhenExposingAll() {
    contextRunner
        .withAllowBeanDefinitionOverriding(true)
        .withBean(
            QueryHandlerRegistry.class,
            RabbitMqQueryBusAutoConfigurationTest::registryWithLocalMessages)
        .withPropertyValues("cqrs.rabbitmq.expose=all")
        .run(
            context -> {
              Declarables declarables = context.getBean("cqrsQueryDeclarables", Declarables.class);
              MessageNamingStrategy naming = context.getBean(MessageNamingStrategy.class);
              assertThat(messageBindings(declarables, "cqrs.queries"))
                  .containsExactlyInAnyOrder(
                      naming.queryName(TestQuery.class), naming.queryName(LocalQuery.class))
                  .doesNotContain(naming.queryName(TestOrderListQuery.class));
              assertThat(
                      context
                          .getBean(
                              "cqrsQueryListenerContainer", SimpleMessageListenerContainer.class)
                          .getMessageListener())
                  .extracting("delegate")
                  .extracting("exposure")
                  .isEqualTo(RabbitMqExposure.ALL);
            });
  }

  @Test
  void queryBusRunsTheOutboundMiddlewareBeansBeforeSending() {
    contextRunner
        .withBean("outboundBlocker", BusMiddleware.class, OutboundBlocker::new)
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(RabbitMqQueryBus.class).ask(new TestQuery("data")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(OutboundBlocker.MESSAGE));
  }

  @Test
  void withoutAReplyTimeoutTheBusUsesTheSharedPublisher() {
    contextRunner.run(
        context -> {
          assertThat(context)
              .doesNotHaveBean(RabbitMqQueryBusAutoConfiguration.QUERY_RABBIT_TEMPLATE)
              .doesNotHaveBean(RabbitMqQueryBusAutoConfiguration.QUERY_PUBLISHER);
          assertThat(context.getBean(RabbitMqQueryBus.class))
              .extracting("publisher")
              .isSameAs(context.getBean(RabbitMqPublisher.class));
        });
  }

  @Test
  void withAReplyTimeoutTheBusSendsThroughItsOwnTemplateConfiguredLikeBoots() {
    contextRunner
        .withPropertyValues(
            "cqrs.rabbitmq.queries.reply-timeout=750ms",
            "spring.rabbitmq.template.mandatory=true",
            "spring.rabbitmq.template.observation-enabled=true",
            "spring.rabbitmq.template.retry.enabled=true")
        .withBean(
            "routingKeyCustomizer",
            RabbitTemplateCustomizer.class,
            () -> template -> template.setRoutingKey("customized"))
        .run(
            context -> {
              RabbitTemplate shared = context.getBean("rabbitTemplate", RabbitTemplate.class);
              RabbitTemplate own =
                  context.getBean(
                      RabbitMqQueryBusAutoConfiguration.QUERY_RABBIT_TEMPLATE,
                      RabbitTemplate.class);
              RabbitMqPublisher ownPublisher =
                  context.getBean(
                      RabbitMqQueryBusAutoConfiguration.QUERY_PUBLISHER, RabbitMqPublisher.class);

              assertThat(own).isNotSameAs(shared);
              assertThat(ReflectionTestUtils.getField(own, "replyTimeout")).isEqualTo(750L);
              assertThat(ReflectionTestUtils.getField(shared, "replyTimeout")).isEqualTo(5000L);
              assertThat(own.isMandatoryFor(null)).isTrue();
              assertThat(ReflectionTestUtils.getField(own, "observationEnabled")).isEqualTo(true);
              assertThat(ReflectionTestUtils.getField(own, "retryTemplate")).isNotNull();
              assertThat(own.getRoutingKey()).isEqualTo("customized");
              assertThat(own.getMessageConverter()).isSameAs(shared.getMessageConverter());
              assertThat(ownPublisher).extracting("rabbitTemplate").isSameAs(own);
              assertThat(context.getBean(RabbitMqQueryBus.class))
                  .extracting("publisher")
                  .isSameAs(ownPublisher);
              assertThat(context.getBeanProvider(RabbitTemplate.class).getIfUnique())
                  .isSameAs(shared);
              assertThat(context.getBeanProvider(RabbitMqPublisher.class).getIfUnique())
                  .isSameAs(context.getBean("cqrsRabbitMqPublisher"));
            });
  }

  @Test
  void theBusUsesATemplateTheApplicationDefinesUnderItsName() {
    RabbitTemplate custom = new RabbitTemplate(mock(ConnectionFactory.class));
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.queries.reply-timeout=750ms")
        .withBean(
            RabbitMqQueryBusAutoConfiguration.QUERY_RABBIT_TEMPLATE,
            RabbitTemplate.class,
            () -> custom,
            definition -> ((AbstractBeanDefinition) definition).setDefaultCandidate(false))
        .run(
            context ->
                assertThat(context.getBean(RabbitMqQueryBus.class))
                    .extracting("publisher")
                    .extracting("rabbitTemplate")
                    .isSameAs(custom));
  }

  @Test
  void queryListenerReadsMessagesByTheirLogicalName() {
    contextRunner.run(
        context ->
            assertThat(
                    context
                        .getBean("cqrsQueryListenerContainer", SimpleMessageListenerContainer.class)
                        .getMessageListener())
                .extracting("messageConverter")
                .isInstanceOf(MessageNameResolvingConverter.class));
  }
}
