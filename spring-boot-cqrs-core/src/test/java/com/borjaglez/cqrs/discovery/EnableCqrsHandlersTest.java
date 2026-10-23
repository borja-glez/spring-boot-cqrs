package com.borjaglez.cqrs.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.fixtures.scan.ScannedHandlersConfiguration;
import com.borjaglez.cqrs.fixtures.scan.command.ScannedCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.event.ScannedEventHandler;
import com.borjaglez.cqrs.fixtures.scan.query.ScannedQueryHandler;

class EnableCqrsHandlersTest {

  @Test
  void registersHandlersInTheAnnotatedClassPackage() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(ScannedHandlersConfiguration.class)) {
      assertThat(context.getBeansOfType(ScannedCommandHandler.class)).hasSize(1);
      assertThat(context.getBeansOfType(ScannedQueryHandler.class)).hasSize(1);
      assertThat(context.getBeansOfType(ScannedEventHandler.class)).hasSize(1);
    }
  }

  @Test
  void registersHandlersInExplicitBasePackages() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(BasePackagesConfiguration.class)) {
      assertThat(context.getBeansOfType(ScannedCommandHandler.class)).hasSize(1);
      assertThat(context.getBeansOfType(ScannedQueryHandler.class)).hasSize(1);
      assertThat(context.getBeansOfType(ScannedEventHandler.class)).isEmpty();
    }
  }

  @Test
  void registersHandlersInBasePackageClasses() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(BasePackageClassesConfiguration.class)) {
      assertThat(context.getBeansOfType(ScannedEventHandler.class)).hasSize(1);
      assertThat(context.getBeansOfType(ScannedCommandHandler.class)).isEmpty();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableCqrsHandlers(
      basePackages = {
        "com.borjaglez.cqrs.fixtures.scan.command",
        "com.borjaglez.cqrs.fixtures.scan.query"
      })
  static class BasePackagesConfiguration {}

  @Configuration(proxyBeanMethods = false)
  @EnableCqrsHandlers(basePackageClasses = ScannedEventHandler.class)
  static class BasePackageClassesConfiguration {}
}
