package com.borjaglez.cqrs.discovery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/** Registers the handler classes of the packages named by {@link EnableCqrsHandlers}. */
class EnableCqrsHandlersRegistrar
    implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {

  private Environment environment;
  private ResourceLoader resourceLoader;

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
    AnnotationAttributes attributes =
        AnnotationAttributes.fromMap(
            importingClassMetadata.getAnnotationAttributes(
                EnableCqrsHandlers.class.getName(), true));
    Set<String> packages = new LinkedHashSet<>(List.of(attributes.getStringArray("basePackages")));
    for (String basePackageClass : attributes.getStringArray("basePackageClasses")) {
      packages.add(ClassUtils.getPackageName(basePackageClass));
    }
    if (packages.isEmpty()) {
      packages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
    }
    new HandlerTypeScanner(environment, resourceLoader).register(registry, packages);
  }
}
