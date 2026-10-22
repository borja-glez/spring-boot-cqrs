package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.transaction.QueryTransactions;
import com.borjaglez.cqrs.transaction.TransactionMiddleware;

class CqrsTransactionAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsTransactionAutoConfiguration.class))
          .withUserConfiguration(TransactionManagerConfiguration.class);

  @Test
  void registersMiddlewareWhenEnabled() {
    contextRunner
        .withPropertyValues("cqrs.transactions.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TransactionMiddleware.class);
              assertThat(context.getBeansOfType(BusMiddleware.class).values())
                  .contains(context.getBean(TransactionMiddleware.class));
            });
  }

  @Test
  void noMiddlewareByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(TransactionMiddleware.class));
  }

  @Test
  void noMiddlewareWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.transactions.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TransactionMiddleware.class));
  }

  @Test
  void noMiddlewareWithoutTransactionManager() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CqrsAutoConfiguration.class, CqrsTransactionAutoConfiguration.class))
        .withPropertyValues("cqrs.transactions.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(TransactionMiddleware.class));
  }

  @Test
  void noMiddlewareWithSeveralTransactionManagersAndNoPrimary() {
    contextRunner
        .withUserConfiguration(SecondTransactionManagerConfiguration.class)
        .withPropertyValues("cqrs.transactions.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(TransactionMiddleware.class));
  }

  @Test
  void usesThePrimaryTransactionManager() {
    contextRunner
        .withUserConfiguration(PrimaryTransactionManagerConfiguration.class)
        .withPropertyValues("cqrs.transactions.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TransactionMiddleware.class));
  }

  @Test
  void backsOffWhenTheApplicationDefinesItsOwnMiddleware() {
    contextRunner
        .withUserConfiguration(CustomMiddlewareConfiguration.class)
        .withPropertyValues("cqrs.transactions.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TransactionMiddleware.class);
              assertThat(context.getBean(TransactionMiddleware.class))
                  .isSameAs(context.getBean("customTransactionMiddleware"));
            });
  }

  @Test
  void queriesAreReadOnlyByDefault() {
    contextRunner
        .withPropertyValues("cqrs.transactions.enabled=true")
        .run(
            context ->
                assertThat(context.getBean(CqrsProperties.class).getTransactions().getQueries())
                    .isEqualTo(QueryTransactions.READ_ONLY));
  }

  @ParameterizedTest
  @CsvSource({"read-only,READ_ONLY", "read-write,READ_WRITE", "none,NONE"})
  void queriesProperty(String value, QueryTransactions expected) {
    contextRunner
        .withPropertyValues("cqrs.transactions.enabled=true", "cqrs.transactions.queries=" + value)
        .run(
            context -> {
              assertThat(context.getBean(CqrsProperties.class).getTransactions().getQueries())
                  .isEqualTo(expected);
              assertThat(context).hasSingleBean(TransactionMiddleware.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class TransactionManagerConfiguration {
    @Bean
    PlatformTransactionManager transactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class SecondTransactionManagerConfiguration {
    @Bean
    PlatformTransactionManager otherTransactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class PrimaryTransactionManagerConfiguration {
    @Bean
    @Primary
    PlatformTransactionManager primaryTransactionManager() {
      return mock(PlatformTransactionManager.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomMiddlewareConfiguration {
    @Bean
    TransactionMiddleware customTransactionMiddleware(
        List<PlatformTransactionManager> transactionManagers) {
      return new TransactionMiddleware(transactionManagers.get(0), QueryTransactions.NONE);
    }
  }
}
