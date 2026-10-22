package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.javapoet.ClassName;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;
import com.borjaglez.cqrs.fixtures.scan.application.ImplicitComponentScanConfiguration;
import com.borjaglez.cqrs.fixtures.scan.application.ScannedCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.component.ComponentCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.componentscan.ComponentScannedCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.conditional.ConditionalCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.later.LaterCommand;
import com.borjaglez.cqrs.fixtures.scan.later.LaterCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.later.LaterPackageAutoConfiguration;
import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.introspection.HandlerDescriptor;

class CqrsHandlersAutoConfigurationTest {

  private static final String APPLICATION_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.application";
  private static final String CONDITIONAL_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.conditional";
  private static final String LATER_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.later";
  private static final String ON_DEMAND_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.ondemand";

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
  void scanSeesPackagesRegisteredByLaterAutoConfigurations() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(LaterPackageAutoConfiguration.class))
        .withInitializer(
            context ->
                AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context, APPLICATION_PACKAGE))
        .run(
            context -> {
              assertThat(AutoConfigurationPackages.get(context))
                  .containsExactly(APPLICATION_PACKAGE, LATER_PACKAGE);
              assertThat(context).hasSingleBean(ScannedCommandHandler.class);
              assertThat(context).hasSingleBean(LaterCommandHandler.class);
              String result =
                  context.getBean(CommandBus.class).dispatchAndReceive(new LaterCommand());
              assertThat(result).isEqualTo("later");
            });
  }

  @Test
  void scanDoesNotInstantiateTheAutoConfigurationPackagesBeanEarly() {
    PackagesBeanProbe probe = new PackagesBeanProbe();
    contextRunner
        .withInitializer(
            context -> {
              AutoConfigurationPackages.register(
                  (BeanDefinitionRegistry) context, APPLICATION_PACKAGE);
              context.addBeanFactoryPostProcessor(probe);
            })
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(probe.handlerRegistered).isTrue();
              assertThat(probe.packagesBeanCreated).isFalse();
            });
  }

  @Test
  void introspectionReportsScannedHandlers() {
    contextRunner
        .withInitializer(
            context ->
                AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context, APPLICATION_PACKAGE))
        .run(
            context -> {
              // The scanned handler is registered, and so created, after the introspection.
              assertThat(List.of(context.getBeanDefinitionNames()))
                  .containsSubsequence("cqrsIntrospection", "scannedCommandHandler");
              assertThat(context.getBean(CqrsIntrospection.class).getHandlers())
                  .extracting(HandlerDescriptor::handlerBeanType)
                  .containsExactly(ScannedCommandHandler.class);
            });
  }

  @Test
  void scannedHandlersAreCreatedBeforeTheBeansOfLaterAutoConfigurations() {
    contextRunner
        .withConfiguration(AutoConfigurations.of(RegistryReadingAutoConfiguration.class))
        .withInitializer(
            context ->
                AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context, APPLICATION_PACKAGE))
        .run(
            context ->
                assertThat(context.getBean(RegisteredCommands.class).types())
                    .containsExactly(ScannedCommand.class));
  }

  @Test
  void handlersCreatedOnDemandAreNotCreatedOnStartup() {
    contextRunner
        .withInitializer(
            context ->
                AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context, ON_DEMAND_PACKAGE))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeanFactory().getBeanDefinitionNames())
                  .contains(
                      "lazyCommandHandler", "prototypeCommandHandler", "scopedProxyCommandHandler");
              assertThat(context.getBean(CommandHandlerRegistry.class).getRegisteredCommands())
                  .isEmpty();
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

  @Test
  void handlerScanIsLeftOutOfTheAotGeneratedCode() throws IOException {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      AutoConfigurationPackages.register(context, APPLICATION_PACKAGE);
      context.register(CqrsHandlersAutoConfiguration.class);
      InMemoryGeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
      DefaultGenerationContext generationContext =
          new DefaultGenerationContext(
              new ClassNameGenerator(ClassName.get(getClass())), generatedFiles);

      new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
      generationContext.writeGeneratedContent();

      StringBuilder code = new StringBuilder();
      for (InputStreamSource source : generatedFiles.getGeneratedFiles(Kind.SOURCE).values()) {
        code.append(new String(source.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
      }
      // The generated code registers the scanned handlers in their place; it does not scan again.
      assertThat(code)
          .contains(ScannedCommandHandler.class.getSimpleName())
          .contains("setDependsOn(\"scannedCommandHandler\")")
          .doesNotContain(CqrsHandlersRegistrar.HandlerScan.class.getSimpleName());
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

  /**
   * Comes after the handler scan and reads the handler registry when its bean is created, as the
   * declarations of the RabbitMQ adapter do.
   */
  @AutoConfiguration(after = CqrsHandlersAutoConfiguration.class)
  static class RegistryReadingAutoConfiguration {

    @Bean
    RegisteredCommands registeredCommands(CommandHandlerRegistry registry) {
      return new RegisteredCommands(Set.copyOf(registry.getRegisteredCommands()));
    }
  }

  record RegisteredCommands(Set<Class<?>> types) {}

  /**
   * Looks at the bean factory once its bean definitions are complete: it runs after every {@code
   * BeanDefinitionRegistryPostProcessor}, before the beans of the application are created.
   */
  static class PackagesBeanProbe implements BeanFactoryPostProcessor {

    private boolean handlerRegistered;
    private boolean packagesBeanCreated;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      handlerRegistered = beanFactory.containsBeanDefinition("scannedCommandHandler");
      packagesBeanCreated =
          beanFactory.containsSingleton(AutoConfigurationPackages.class.getName());
    }
  }
}
