package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.context.MessageContextThreadLocalAccessor;
import com.borjaglez.cqrs.middleware.DispatchPhase;

import io.micrometer.context.ContextRegistry;

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

  @Test
  void threadLocalAccessorIsRegisteredInTheGlobalContextRegistry() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(MessageContextThreadLocalAccessor.class);
          assertThat(ContextRegistry.getInstance().getThreadLocalAccessors())
              .anyMatch(accessor -> MessageContextThreadLocalAccessor.KEY.equals(accessor.key()));
        });
  }

  @Test
  void threadLocalAccessorIsNotRegisteredWhenContextPropagationIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("io.micrometer.context."))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ContextPropagationMiddleware.class);
              assertThat(context).doesNotHaveBean(MessageContextThreadLocalAccessor.class);
            });
  }

  @Test
  void threadLocalAccessorIsNotRegisteredWhenContextIsDisabled() {
    contextRunner
        .withPropertyValues("cqrs.context.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(MessageContextThreadLocalAccessor.class));
  }

  @Test
  void contextPropagatingTaskDecoratorCarriesTheMessageContext() {
    contextRunner.run(
        context -> {
          ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
          executor.setCorePoolSize(1);
          executor.setMaxPoolSize(1);
          executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
          executor.initialize();
          AtomicReference<String> seen = new AtomicReference<>("unset");
          try {
            try (MessageContext.Scope ignored =
                MessageContext.scope(MessageContext.empty().with("correlationId", "req-1"))) {
              executor.submit(() -> seen.set(MessageContext.current().correlationId())).get();
            }
            assertThat(seen.get()).isEqualTo("req-1");

            executor.submit(() -> seen.set(MessageContext.current().correlationId())).get();
            assertThat(seen.get()).isNull();
          } finally {
            executor.shutdown();
            MessageContext.clear();
          }
        });
  }

  @Test
  void contextMiddlewareAlsoRunsOnTheSenderOfRemoteBuses() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(ContextPropagationMiddleware.class).phases())
                .containsExactlyInAnyOrder(
                    DispatchPhase.LOCAL, DispatchPhase.OUTBOUND, DispatchPhase.INBOUND));
  }
}
