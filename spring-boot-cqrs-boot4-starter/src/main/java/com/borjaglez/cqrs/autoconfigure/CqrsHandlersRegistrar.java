package com.borjaglez.cqrs.autoconfigure;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.borjaglez.cqrs.discovery.HandlerTypeScanner;

/**
 * Registers the handler classes of the application packages: the auto-configuration packages (the
 * package of the {@code @SpringBootApplication} class and those added with
 * {@code @AutoConfigurationPackage}) and the base packages of every {@code @ComponentScan} declared
 * by a registered bean definition, the implicit one of {@code @SpringBootApplication} included. The
 * filters of the component scans are not applied, except the {@link TypeExcludeFilter} beans.
 *
 * <p>The scan is a {@link BeanDefinitionRegistryPostProcessor}, so it runs once every configuration
 * class, auto-configurations included, has been processed: it sees every package whatever the order
 * of the auto-configurations, and it skips the handlers the configuration already registered. It
 * reads the auto-configuration packages of this context from their bean definition: creating their
 * bean would freeze them, and the packages added by later auto-configurations would be lost for
 * everyone, Spring Data repositories and entity scanning included.
 *
 * <p>The handlers keep the place of the auto-configuration in the creation order of the beans (see
 * {@link ScannedHandlers}), although their definitions are registered last.
 */
class CqrsHandlersRegistrar
    implements ImportBeanDefinitionRegistrar,
        BeanFactoryAware,
        EnvironmentAware,
        ResourceLoaderAware {

  private BeanFactory beanFactory;
  private Environment environment;
  private ResourceLoader resourceLoader;

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    RootBeanDefinition scan =
        new RootBeanDefinition(
            HandlerScan.class, () -> new HandlerScan(beanFactory, environment, resourceLoader));
    scan.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    // The code generated ahead of time registers the handlers found at build time.
    scan.setAttribute(BeanRegistrationAotProcessor.IGNORE_REGISTRATION_ATTRIBUTE, true);
    registry.registerBeanDefinition(HandlerScan.class.getName(), scan);
    RootBeanDefinition scannedHandlers = new RootBeanDefinition(ScannedHandlers.class);
    scannedHandlers.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    registry.registerBeanDefinition(ScannedHandlers.class.getName(), scannedHandlers);
  }

  /**
   * Gives the scanned handlers the place of the handler auto-configuration in the creation order of
   * the beans. Its definition is registered with the auto-configuration and depends on the handlers
   * the context creates on startup, so they are created there, before the beans of the
   * auto-configurations that come after it; some of those read the handler registries when they are
   * created, such as the declarations of the RabbitMQ adapter.
   */
  static final class ScannedHandlers {}

  /** The scan of the handler classes, once every configuration class has been processed. */
  static final class HandlerScan implements BeanDefinitionRegistryPostProcessor {

    private final BeanFactory beanFactory;
    private final Environment environment;
    private final ResourceLoader resourceLoader;

    HandlerScan(BeanFactory beanFactory, Environment environment, ResourceLoader resourceLoader) {
      this.beanFactory = beanFactory;
      this.environment = environment;
      this.resourceLoader = resourceLoader;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
      Set<String> packages = new LinkedHashSet<>(autoConfigurationPackages(registry));
      for (String beanName : registry.getBeanDefinitionNames()) {
        if (registry.getBeanDefinition(beanName) instanceof AnnotatedBeanDefinition definition) {
          packages.addAll(componentScanPackages(definition.getMetadata()));
        }
      }
      HandlerTypeScanner scanner = new HandlerTypeScanner(environment, resourceLoader);
      scanner.addExcludeFilter(typeExcludeFilter());
      String[] createdOnStartup =
          scanner.register(registry, packages).stream()
              .filter(handler -> isCreatedOnStartup(registry.getBeanDefinition(handler)))
              .toArray(String[]::new);
      registry.getBeanDefinition(ScannedHandlers.class.getName()).setDependsOn(createdOnStartup);
    }

    /**
     * Whether the context creates the bean on startup: a singleton, not lazy, and not a scoped
     * proxy, whose object is created when it is first used.
     */
    private static boolean isCreatedOnStartup(BeanDefinition definition) {
      return definition.isSingleton()
          && !definition.isLazyInit()
          && definition.getOriginatingBeanDefinition() == null;
    }

    /**
     * The auto-configuration packages of the registry, read from their bean definition in a bean
     * factory of its own: {@link AutoConfigurationPackages#get} would create their bean in the
     * context.
     */
    private static List<String> autoConfigurationPackages(BeanDefinitionRegistry registry) {
      String beanName = AutoConfigurationPackages.class.getName();
      if (!registry.containsBeanDefinition(beanName)) {
        return List.of();
      }
      DefaultListableBeanFactory definitionOnly = new DefaultListableBeanFactory();
      definitionOnly.registerBeanDefinition(
          beanName, new GenericBeanDefinition(registry.getBeanDefinition(beanName)));
      return AutoConfigurationPackages.get(definitionOnly);
    }

    /**
     * Applies the {@link TypeExcludeFilter} beans, as the component scan of
     * {@code @SpringBootApplication} does; Spring Boot's test support registers one that leaves out
     * the classes nested in test classes.
     */
    private TypeExcludeFilter typeExcludeFilter() {
      TypeExcludeFilter typeExcludeFilter = new TypeExcludeFilter();
      typeExcludeFilter.setBeanFactory(beanFactory);
      return typeExcludeFilter;
    }

    /** The base packages of the component scans of a class, resolved as Spring resolves them. */
    private Set<String> componentScanPackages(AnnotationMetadata metadata) {
      Set<String> packages = new LinkedHashSet<>();
      for (AnnotationAttributes componentScan :
          metadata.getMergedRepeatableAnnotationAttributes(
              ComponentScan.class, ComponentScans.class, true)) {
        Set<String> basePackages = new LinkedHashSet<>();
        for (String basePackage : componentScan.getStringArray("basePackages")) {
          Collections.addAll(
              basePackages,
              StringUtils.tokenizeToStringArray(
                  environment.resolvePlaceholders(basePackage),
                  ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
        }
        for (String basePackageClass : componentScan.getStringArray("basePackageClasses")) {
          basePackages.add(ClassUtils.getPackageName(basePackageClass));
        }
        if (basePackages.isEmpty()) {
          basePackages.add(ClassUtils.getPackageName(metadata.getClassName()));
        }
        packages.addAll(basePackages);
      }
      return packages;
    }
  }
}
