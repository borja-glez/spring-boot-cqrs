package com.borjaglez.cqrs.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.AssignableTypeFilter;

import com.borjaglez.cqrs.fixtures.scan.command.AbstractScannedCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.command.ScannedCommandHandler;
import com.borjaglez.cqrs.fixtures.scan.event.ScannedEventHandler;
import com.borjaglez.cqrs.fixtures.scan.query.ScannedQueryHandler;

class HandlerTypeScannerTest {

  private static final String COMMAND_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.command";
  private static final String QUERY_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.query";
  private static final String EVENT_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.event";
  private static final String UNRELATED_PACKAGE = "com.borjaglez.cqrs.fixtures.scan.unrelated";

  private final GenericApplicationContext context = new GenericApplicationContext();

  // The registry Spring hands to an ImportBeanDefinitionRegistrar is the context's bean factory.
  private final DefaultListableBeanFactory registry = context.getDefaultListableBeanFactory();

  private final HandlerTypeScanner scanner =
      new HandlerTypeScanner(context.getEnvironment(), context);

  @AfterEach
  void closeContext() {
    context.close();
  }

  @Test
  void registersAnnotatedHandlerWithoutStereotype() {
    Set<String> registered = scanner.register(registry, List.of(COMMAND_PACKAGE));

    assertThat(registered).containsExactly("scannedCommandHandler");
    assertThat(registry.getBeanDefinition("scannedCommandHandler").getBeanClassName())
        .isEqualTo(ScannedCommandHandler.class.getName());
    context.refresh();
    assertThat(context.getBeansOfType(ScannedCommandHandler.class))
        .containsOnlyKeys("scannedCommandHandler");
  }

  @Test
  void registersQueryAndEventHandlers() {
    Set<String> registered = scanner.register(registry, List.of(QUERY_PACKAGE, EVENT_PACKAGE));

    assertThat(registered).containsExactly("scannedQueryHandler", "scannedEventHandler");
    context.refresh();
    assertThat(context.getBean(ScannedQueryHandler.class)).isNotNull();
    assertThat(context.getBean(ScannedEventHandler.class)).isNotNull();
  }

  @Test
  void appliesTheBeanDefinitionAnnotationsOfTheHandler() {
    scanner.register(registry, List.of(COMMAND_PACKAGE, EVENT_PACKAGE));

    assertThat(registry.getBeanDefinition("scannedEventHandler").isLazyInit()).isTrue();
    assertThat(registry.getBeanDefinition("scannedCommandHandler").isLazyInit()).isFalse();
  }

  @Test
  void skipsHandlerAlreadyRegisteredByClassName() {
    registry.registerBeanDefinition(
        "existingHandler",
        BeanDefinitionBuilder.genericBeanDefinition(ScannedCommandHandler.class.getName())
            .getBeanDefinition());

    Set<String> registered = scanner.register(registry, List.of(COMMAND_PACKAGE));

    assertThat(registered).isEmpty();
    context.refresh();
    assertThat(context.getBeansOfType(ScannedCommandHandler.class))
        .containsOnlyKeys("existingHandler");
  }

  @Test
  void skipsHandlerAlreadyDeclaredByBeanMethod() {
    context.registerBean("beanMethodConfiguration", BeanMethodConfiguration.class);
    // Loads the @Bean methods, as Spring does for user configuration before auto-configuration.
    new ConfigurationClassPostProcessor().postProcessBeanDefinitionRegistry(registry);

    Set<String> registered = scanner.register(registry, List.of(COMMAND_PACKAGE));

    assertThat(registered).isEmpty();
    context.refresh();
    assertThat(context.getBeansOfType(ScannedCommandHandler.class))
        .containsOnlyKeys("handlerFromBeanMethod");
  }

  @Test
  void skipsHandlerMatchedByAnExcludeFilter() {
    scanner.addExcludeFilter(new AssignableTypeFilter(ScannedCommandHandler.class));

    Set<String> registered = scanner.register(registry, List.of(COMMAND_PACKAGE));

    assertThat(registered).isEmpty();
    assertThat(registry.getBeanDefinitionCount()).isZero();
  }

  @Test
  void registersIntoARegistryThatIsNotABeanFactory() {
    SimpleBeanDefinitionRegistry simpleRegistry = new SimpleBeanDefinitionRegistry();

    Set<String> registered = scanner.register(simpleRegistry, List.of(COMMAND_PACKAGE));

    assertThat(registered).containsExactly("scannedCommandHandler");
    assertThat(simpleRegistry.getBeanDefinition("scannedCommandHandler").getBeanClassName())
        .isEqualTo(ScannedCommandHandler.class.getName());
  }

  @Test
  void ignoresHandlersOutsideBasePackages() {
    Set<String> registered = scanner.register(registry, List.of(UNRELATED_PACKAGE));

    assertThat(registered).isEmpty();
    assertThat(registry.getBeanDefinitionNames()).isEmpty();
  }

  @Test
  void ignoresAbstractHandlerClasses() {
    scanner.register(registry, List.of(COMMAND_PACKAGE));

    assertThat(registry.getBeanDefinitionNames())
        .noneMatch(
            name ->
                AbstractScannedCommandHandler.class
                    .getName()
                    .equals(registry.getBeanDefinition(name).getBeanClassName()));
  }

  @Test
  void returnsEmptyForNoPackages() {
    Set<String> registered = scanner.register(registry, List.of());

    assertThat(registered).isEmpty();
    assertThat(registry.getBeanDefinitionCount()).isZero();
  }

  @Configuration(proxyBeanMethods = false)
  static class BeanMethodConfiguration {

    @Bean
    ScannedCommandHandler handlerFromBeanMethod() {
      return new ScannedCommandHandler();
    }
  }
}
