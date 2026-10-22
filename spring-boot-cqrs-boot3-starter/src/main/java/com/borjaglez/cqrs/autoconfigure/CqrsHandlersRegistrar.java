package com.borjaglez.cqrs.autoconfigure;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
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
 * package of the {@code @SpringBootApplication} class) and the base packages of every
 * {@code @ComponentScan} declared by a registered bean definition, the implicit one of
 * {@code @SpringBootApplication} included. Auto-configurations run after the user's configuration
 * has been loaded, so the handlers the application already registered itself are skipped. The
 * filters of the component scans are not applied, except the {@link TypeExcludeFilter} beans.
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
    Set<String> packages = new LinkedHashSet<>();
    if (AutoConfigurationPackages.has(beanFactory)) {
      packages.addAll(AutoConfigurationPackages.get(beanFactory));
    }
    for (String beanName : registry.getBeanDefinitionNames()) {
      if (registry.getBeanDefinition(beanName) instanceof AnnotatedBeanDefinition definition) {
        packages.addAll(componentScanPackages(definition.getMetadata()));
      }
    }
    HandlerTypeScanner scanner = new HandlerTypeScanner(environment, resourceLoader);
    scanner.addExcludeFilter(typeExcludeFilter());
    scanner.register(registry, packages);
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
