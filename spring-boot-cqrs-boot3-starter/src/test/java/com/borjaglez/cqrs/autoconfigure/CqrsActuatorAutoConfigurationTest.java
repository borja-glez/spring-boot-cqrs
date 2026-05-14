package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.actuator.CqrsEndpoint;
import com.borjaglez.cqrs.actuator.CqrsInfoContributor;

class CqrsActuatorAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class,
                  CqrsActuatorAutoConfiguration.class,
                  EndpointAutoConfiguration.class,
                  WebEndpointAutoConfiguration.class,
                  InfoContributorAutoConfiguration.class))
          .withPropertyValues("management.endpoints.web.exposure.include=cqrs,info");

  @Test
  void endpointRegisteredByDefault() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(CqrsEndpoint.class));
  }

  @Test
  void endpointNotRegisteredWhenActuatorClassMissing() {
    contextRunner
        .withClassLoader(
            new FilteredClassLoader(
                "org.springframework.boot.actuate.endpoint.annotation.Endpoint"))
        .run(context -> assertThat(context).doesNotHaveBean(CqrsEndpoint.class));
  }

  @Test
  void endpointHonorsManagementEnabledFalse() {
    contextRunner
        .withPropertyValues("management.endpoint.cqrs.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(CqrsEndpoint.class));
  }

  @Test
  void infoContributorRegisteredByDefault() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(CqrsInfoContributor.class));
  }

  @Test
  void infoContributorHonorsDisabledFlag() {
    contextRunner
        .withPropertyValues("management.info.cqrs.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(CqrsInfoContributor.class));
  }
}
