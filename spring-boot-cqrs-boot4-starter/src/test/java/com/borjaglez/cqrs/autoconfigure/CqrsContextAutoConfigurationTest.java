package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;

class CqrsContextAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsContextAutoConfiguration.class));

  @Test
  void contextMiddlewareIsRegisteredByDefault() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(ContextPropagationMiddleware.class));
  }

  @Test
  void contextMiddlewareIsNotRegisteredWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.context.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ContextPropagationMiddleware.class));
  }

  @Test
  void contextMiddlewareIsNotRegisteredWhenSlf4jIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.slf4j.MDC"))
        .run(context -> assertThat(context).doesNotHaveBean(ContextPropagationMiddleware.class));
  }

  @Test
  void propertiesAreHonoured() {
    contextRunner
        .withPropertyValues(
            "cqrs.context.auto-correlation-id=false",
            "cqrs.context.mdc-keys=correlationId,tenantId",
            "cqrs.context.header-prefix=cqrs.ctx.")
        .run(
            context -> {
              CqrsProperties properties = context.getBean(CqrsProperties.class);
              CqrsProperties.ContextProperties ctx = properties.getContext();
              assertThat(ctx.isEnabled()).isTrue();
              assertThat(ctx.isAutoCorrelationId()).isFalse();
              assertThat(ctx.getMdcKeys()).isEqualTo(List.of("correlationId", "tenantId"));
              assertThat(ctx.getHeaderPrefix()).isEqualTo("cqrs.ctx.");
            });
  }
}
