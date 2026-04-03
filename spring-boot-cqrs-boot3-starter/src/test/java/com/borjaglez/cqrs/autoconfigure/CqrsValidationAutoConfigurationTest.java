package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.validation.CommandValidationInterceptor;

class CqrsValidationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsValidationAutoConfiguration.class));

  @Test
  void commandValidationInterceptorIsCreatedWhenValidatorIsPresent() {
    contextRunner
        .withUserConfiguration(ValidatorConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CommandValidationInterceptor.class);
            });
  }

  @Test
  void commandValidationInterceptorIsNotCreatedWhenValidatorClassIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(Validator.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(CommandValidationInterceptor.class);
            });
  }

  @Test
  void commandValidationInterceptorIsNotCreatedWhenNoValidatorBeanExists() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(CommandValidationInterceptor.class);
        });
  }

  @Test
  void commandValidationInterceptorIsNotCreatedWhenDisabledByProperty() {
    contextRunner
        .withUserConfiguration(ValidatorConfiguration.class)
        .withPropertyValues("cqrs.validation.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(CommandValidationInterceptor.class);
            });
  }

  @Test
  void commandValidationInterceptorIsCreatedWhenEnabledExplicitly() {
    contextRunner
        .withUserConfiguration(ValidatorConfiguration.class)
        .withPropertyValues("cqrs.validation.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CommandValidationInterceptor.class);
            });
  }

  @Test
  void validationEnabledByDefaultWhenPropertyNotSet() {
    contextRunner
        .withUserConfiguration(ValidatorConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CommandValidationInterceptor.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class ValidatorConfiguration {
    @Bean
    Validator validator() {
      return Validation.buildDefaultValidatorFactory().getValidator();
    }
  }
}
