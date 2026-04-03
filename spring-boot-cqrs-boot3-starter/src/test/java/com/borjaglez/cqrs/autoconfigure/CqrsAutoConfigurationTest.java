package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.discovery.BeanPostProcessorHandlerDiscoverer;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.event.spring.SpringEventBus;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.query.spring.SpringQueryBus;

class CqrsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class));

  @Test
  void autoConfigurationCreatesDefaultBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(MessageNamingStrategy.class);
          assertThat(context).hasSingleBean(CommandHandlerRegistry.class);
          assertThat(context).hasSingleBean(EventHandlerRegistry.class);
          assertThat(context).hasSingleBean(QueryHandlerRegistry.class);
          assertThat(context).hasSingleBean(BeanPostProcessorHandlerDiscoverer.class);
          assertThat(context).hasSingleBean(CommandBus.class);
          assertThat(context).hasSingleBean(EventBus.class);
          assertThat(context).hasSingleBean(QueryBus.class);
        });
  }

  @Test
  void defaultNamingStrategyIsDefaultMessageNamingStrategy() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(DefaultMessageNamingStrategy.class);
          assertThat(context.getBean(MessageNamingStrategy.class))
              .isInstanceOf(DefaultMessageNamingStrategy.class);
        });
  }

  @Test
  void defaultCommandBusIsSpringCommandBus() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(SpringCommandBus.class);
          assertThat(context.getBean(CommandBus.class)).isInstanceOf(SpringCommandBus.class);
        });
  }

  @Test
  void defaultEventBusIsSpringEventBus() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(SpringEventBus.class);
          assertThat(context.getBean(EventBus.class)).isInstanceOf(SpringEventBus.class);
        });
  }

  @Test
  void defaultQueryBusIsSpringQueryBus() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(SpringQueryBus.class);
          assertThat(context.getBean(QueryBus.class)).isInstanceOf(SpringQueryBus.class);
        });
  }

  @Test
  void customMessageNamingStrategyReplacesDefault() {
    contextRunner
        .withUserConfiguration(CustomNamingStrategyConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MessageNamingStrategy.class);
              assertThat(context.getBean(MessageNamingStrategy.class))
                  .isInstanceOf(CustomMessageNamingStrategy.class);
              assertThat(context).doesNotHaveBean(DefaultMessageNamingStrategy.class);
            });
  }

  @Test
  void customCommandBusReplacesSpringCommandBus() {
    contextRunner
        .withUserConfiguration(CustomCommandBusConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CommandBus.class);
              assertThat(context.getBean(CommandBus.class)).isInstanceOf(CustomCommandBus.class);
              assertThat(context).doesNotHaveBean(SpringCommandBus.class);
            });
  }

  @Test
  void customEventBusReplacesSpringEventBus() {
    contextRunner
        .withUserConfiguration(CustomEventBusConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(EventBus.class);
              assertThat(context.getBean(EventBus.class)).isInstanceOf(CustomEventBus.class);
              assertThat(context).doesNotHaveBean(SpringEventBus.class);
            });
  }

  @Test
  void customQueryBusReplacesSpringQueryBus() {
    contextRunner
        .withUserConfiguration(CustomQueryBusConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(QueryBus.class);
              assertThat(context.getBean(QueryBus.class)).isInstanceOf(CustomQueryBus.class);
              assertThat(context).doesNotHaveBean(SpringQueryBus.class);
            });
  }

  @Test
  void cqrsPropertiesAreBound() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(CqrsProperties.class);
        });
  }

  @Test
  void namingPrefixPropertyIsApplied() {
    contextRunner
        .withPropertyValues("cqrs.naming.prefix=myapp")
        .run(
            context -> {
              CqrsProperties properties = context.getBean(CqrsProperties.class);
              assertThat(properties.getNaming().getPrefix()).isEqualTo("myapp");
            });
  }

  // --- Custom bean configurations ---

  static class CustomMessageNamingStrategy implements MessageNamingStrategy {
    @Override
    public String commandName(Class<?> commandClass) {
      return "custom-command";
    }

    @Override
    public String eventName(Class<?> eventClass) {
      return "custom-event";
    }

    @Override
    public String queryName(Class<?> queryClass) {
      return "custom-query";
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomNamingStrategyConfiguration {
    @Bean
    MessageNamingStrategy messageNamingStrategy() {
      return new CustomMessageNamingStrategy();
    }
  }

  static class CustomCommandBus implements CommandBus {
    @Override
    public void dispatch(Command command) {}

    @Override
    public <R> R dispatchAndReceive(Command command) {
      return null;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomCommandBusConfiguration {
    @Bean
    CommandBus commandBus() {
      return new CustomCommandBus();
    }
  }

  static class CustomEventBus implements EventBus {
    @Override
    public void publish(Event event) {}

    @Override
    public void publish(List<Event> events) {}
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomEventBusConfiguration {
    @Bean
    EventBus eventBus() {
      return new CustomEventBus();
    }
  }

  static class CustomQueryBus implements QueryBus {
    @Override
    public <R> R ask(Query query) {
      return null;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomQueryBusConfiguration {
    @Bean
    QueryBus queryBus() {
      return new CustomQueryBus();
    }
  }
}
