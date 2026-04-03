package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CqrsPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class));

  @Test
  void defaultNamingPrefixIsEmpty() {
    contextRunner.run(
        context -> {
          CqrsProperties properties = context.getBean(CqrsProperties.class);
          assertThat(properties.getNaming().getPrefix()).isEmpty();
        });
  }

  @Test
  void defaultValidationEnabledIsTrue() {
    contextRunner.run(
        context -> {
          CqrsProperties properties = context.getBean(CqrsProperties.class);
          assertThat(properties.getValidation().isEnabled()).isTrue();
        });
  }

  @Test
  void defaultObservabilityEnabledIsTrue() {
    contextRunner.run(
        context -> {
          CqrsProperties properties = context.getBean(CqrsProperties.class);
          assertThat(properties.getObservability().isEnabled()).isTrue();
        });
  }

  @Test
  void customNamingPrefix() {
    contextRunner
        .withPropertyValues("cqrs.naming.prefix=myapp")
        .run(
            context -> {
              CqrsProperties properties = context.getBean(CqrsProperties.class);
              assertThat(properties.getNaming().getPrefix()).isEqualTo("myapp");
            });
  }

  @Test
  void customValidationEnabled() {
    contextRunner
        .withPropertyValues("cqrs.validation.enabled=false")
        .run(
            context -> {
              CqrsProperties properties = context.getBean(CqrsProperties.class);
              assertThat(properties.getValidation().isEnabled()).isFalse();
            });
  }

  @Test
  void customObservabilityEnabled() {
    contextRunner
        .withPropertyValues("cqrs.observability.enabled=false")
        .run(
            context -> {
              CqrsProperties properties = context.getBean(CqrsProperties.class);
              assertThat(properties.getObservability().isEnabled()).isFalse();
            });
  }

  @Test
  void allPropertiesCanBeSetTogether() {
    contextRunner
        .withPropertyValues(
            "cqrs.naming.prefix=custom-prefix",
            "cqrs.validation.enabled=false",
            "cqrs.observability.enabled=false")
        .run(
            context -> {
              CqrsProperties properties = context.getBean(CqrsProperties.class);
              assertThat(properties.getNaming().getPrefix()).isEqualTo("custom-prefix");
              assertThat(properties.getValidation().isEnabled()).isFalse();
              assertThat(properties.getObservability().isEnabled()).isFalse();
            });
  }

  @Test
  void nestedPropertiesAreNeverNull() {
    contextRunner.run(
        context -> {
          CqrsProperties properties = context.getBean(CqrsProperties.class);
          assertThat(properties.getNaming()).isNotNull();
          assertThat(properties.getValidation()).isNotNull();
          assertThat(properties.getObservability()).isNotNull();
        });
  }

  @Test
  void settersWorkForNamingProperties() {
    CqrsProperties.NamingProperties naming = new CqrsProperties.NamingProperties();
    naming.setPrefix("test-prefix");
    assertThat(naming.getPrefix()).isEqualTo("test-prefix");
  }

  @Test
  void settersWorkForValidationProperties() {
    CqrsProperties.ValidationProperties validation = new CqrsProperties.ValidationProperties();
    validation.setEnabled(false);
    assertThat(validation.isEnabled()).isFalse();
  }

  @Test
  void settersWorkForObservabilityProperties() {
    CqrsProperties.ObservabilityProperties observability =
        new CqrsProperties.ObservabilityProperties();
    observability.setEnabled(false);
    assertThat(observability.isEnabled()).isFalse();
  }

  @Test
  void settersWorkForCqrsProperties() {
    CqrsProperties properties = new CqrsProperties();

    CqrsProperties.NamingProperties naming = new CqrsProperties.NamingProperties();
    naming.setPrefix("new-prefix");
    properties.setNaming(naming);

    CqrsProperties.ValidationProperties validation = new CqrsProperties.ValidationProperties();
    validation.setEnabled(false);
    properties.setValidation(validation);

    CqrsProperties.ObservabilityProperties observability =
        new CqrsProperties.ObservabilityProperties();
    observability.setEnabled(false);
    properties.setObservability(observability);

    assertThat(properties.getNaming().getPrefix()).isEqualTo("new-prefix");
    assertThat(properties.getValidation().isEnabled()).isFalse();
    assertThat(properties.getObservability().isEnabled()).isFalse();
  }
}
