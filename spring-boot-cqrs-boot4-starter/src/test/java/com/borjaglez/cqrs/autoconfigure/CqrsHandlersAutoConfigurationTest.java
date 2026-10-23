package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;
import com.borjaglez.cqrs.fixtures.scan.application.ImplicitComponentScanConfiguration;
import com.borjaglez.cqrs.fixtures.scan.application.ScannedCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.component.ComponentCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.componentscan.ComponentScannedCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.conditional.ConditionalCommandHandler;

class CqrsHandlersAutoConfigurationTest {

  private static final String APPLICATION_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.application";
  private static final String CONDITIONAL_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.conditional";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsHandlersAutoConfiguration.class));

  @Test
  void registersHandlersInAutoConfigurationPackages() {
    contextRunner
        .withInitializer(
            context ->
                AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context, APPLICATION_PACKAGE))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ScannedCommandHandler.class);
              String result =
                  context.getBean(CommandBus.class).dispatchAndReceive(new ScannedCommand());
              assertThat(result).isEqualTo("application");
            });
  }

  @Test
  void registersHandlersInComponentScanPackages() {
    contextRunner
        .withUserConfiguration(ComponentScanConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ComponentScannedCommandHandler.class);
              String result =
                  context.getBean(CommandBus.class).dispatchAndReceive(new ScannedCommand());
              assertThat(result).isEqualTo("component-scan");
            });
  }

  @Test
  void registersHandlersInTheComponentScanDeclaringClassPackage() {
    contextRunner
        .withUserConfiguration(ImplicitComponentScanConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ScannedCommandHandler.class);
              String result =
                  context.getBean(CommandBus.class).dispatchAndReceive(new ScannedCommand());
              assertThat(result).isEqualTo("application");
            });
  }

  @Test
  void skipsHandlerAlreadyDeclaredAsComponent() {
    contextRunner
        .withUserConfiguration(ComponentHandlerConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ComponentCommandHandler.class);
              String result =
                  context.getBean(CommandBus.class).dispatchAndReceive(new ScannedCommand());
              assertThat(result).isEqualTo("component");
            });
  }

  @Test
  void skipsHandlersExcludedByTypeExcludeFilters() {
    contextRunner
        .withInitializer(
            context -> {
              AutoConfigurationPackages.register(
                  (BeanDefinitionRegistry) context, APPLICATION_PACKAGE);
              context
                  .getBeanFactory()
                  .registerSingleton(
                      "scannedHandlerExcludeFilter", new ScannedHandlerExcludeFilter());
            })
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ScannedCommandHandler.class);
            });
  }

  @Test
  void registersHandlerWhoseConditionMatches() {
    contextRunner
        .withInitializer(
            context ->
                AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context, CONDITIONAL_PACKAGE))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ConditionalCommandHandler.class);
              String result =
                  context.getBean(CommandBus.class).dispatchAndReceive(new ScannedCommand());
              assertThat(result).isEqualTo("conditional");
            });
  }

  @Test
  void skipsHandlerSwitchedOffByItsCondition() {
    // The application's scan covers the handler's package, and its condition fails.
    contextRunner
        .withBean(ConditionalCommandHandler.OVERRIDE, String.class, () -> "override")
        .withUserConfiguration(ConditionalHandlerConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ConditionalCommandHandler.class);
            });
  }

  @Test
  void doesNothingWhenScanDisabled() {
    contextRunner
        .withInitializer(
            context ->
                AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context, APPLICATION_PACKAGE))
        .withPropertyValues("cqrs.handlers.scan-enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(CqrsHandlersAutoConfiguration.class);
              assertThat(context).doesNotHaveBean(ScannedCommandHandler.class);
            });
  }

  @Test
  void doesNothingWithoutPackages() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(CqrsHandlersAutoConfiguration.class);
          assertThat(context).doesNotHaveBean(ScannedCommandHandler.class);
        });
  }

  @Test
  void registeredHandlersSurviveAotProcessing() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      AutoConfigurationPackages.register(context, APPLICATION_PACKAGE);
      context.register(CqrsHandlersAutoConfiguration.class);

      context.refreshForAotProcessing(new RuntimeHints());

      assertThat(context.getBeanFactory().getBeanNamesForType(ScannedCommandHandler.class))
          .containsExactly("scannedCommandHandler");
    }
  }

  /**
   * The user's scan does not register handlers, which are not components; the handler scan does.
   */
  @Configuration(proxyBeanMethods = false)
  @ComponentScan(basePackages = "com.borjaglez.cqrs.fixtures.scan.componentscan")
  static class ComponentScanConfiguration {}

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(basePackageClasses = ComponentCommandHandler.class)
  static class ComponentHandlerConfiguration {}

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(basePackageClasses = ConditionalCommandHandler.class)
  static class ConditionalHandlerConfiguration {}

  /**
   * Leaves {@link ScannedCommandHandler} out, as Spring Boot's test support leaves out the classes
   * nested in test classes.
   */
  static class ScannedHandlerExcludeFilter extends TypeExcludeFilter {

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory factory) {
      return ScannedCommandHandler.class
          .getName()
          .equals(metadataReader.getClassMetadata().getClassName());
    }
  }
}
