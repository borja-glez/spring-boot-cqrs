package com.borjaglez.cqrs.discovery;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

/**
 * Registers a bean for every handler class found in some packages, so that a class annotated with
 * {@link CommandHandler @CommandHandler}, {@link QueryHandler @QueryHandler} or {@link
 * EventHandler @EventHandler} becomes a handler without being a component itself.
 *
 * <p>The candidates are the concrete, independent classes of the packages (and their sub-packages)
 * annotated, directly or as a meta-annotation, with one of the three handler annotations. A
 * candidate is skipped when the registry already holds it: a bean definition with its class name (a
 * component scan, an explicit registration) or, when the registry is a {@link ListableBeanFactory},
 * any bean of its type (a {@code @Bean} method). Bean names come from {@link
 * AnnotationBeanNameGenerator}, and the definitions honour the annotations a component scan
 * honours, such as {@code @Lazy}, {@code @Primary} and {@code @DependsOn}.
 */
public final class HandlerTypeScanner {

  private final ClassPathScanningCandidateComponentProvider provider;
  private final ClassLoader classLoader;

  public HandlerTypeScanner(Environment environment, ResourceLoader resourceLoader) {
    this.provider = new ClassPathScanningCandidateComponentProvider(false, environment);
    this.provider.setResourceLoader(resourceLoader);
    this.provider.addIncludeFilter(new AnnotationTypeFilter(CommandHandler.class));
    this.provider.addIncludeFilter(new AnnotationTypeFilter(QueryHandler.class));
    this.provider.addIncludeFilter(new AnnotationTypeFilter(EventHandler.class));
    this.classLoader = resourceLoader.getClassLoader();
  }

  /**
   * Leaves out the candidates {@code excludeFilter} matches, as an exclude filter of a component
   * scan does.
   */
  public void addExcludeFilter(TypeFilter excludeFilter) {
    provider.addExcludeFilter(excludeFilter);
  }

  /**
   * Registers the handler classes of {@code basePackages} that {@code registry} does not hold yet.
   *
   * @return the names of the beans registered, in registration order
   */
  public Set<String> register(BeanDefinitionRegistry registry, Collection<String> basePackages) {
    Set<String> registered = new LinkedHashSet<>();
    for (String basePackage : basePackages) {
      for (BeanDefinition candidate : provider.findCandidateComponents(basePackage)) {
        if (!isRegistered(registry, candidate.getBeanClassName())) {
          AnnotationConfigUtils.processCommonDefinitionAnnotations(
              (AnnotatedBeanDefinition) candidate);
          String beanName =
              AnnotationBeanNameGenerator.INSTANCE.generateBeanName(candidate, registry);
          registry.registerBeanDefinition(beanName, candidate);
          registered.add(beanName);
        }
      }
    }
    return registered;
  }

  private boolean isRegistered(BeanDefinitionRegistry registry, String className) {
    for (String name : registry.getBeanDefinitionNames()) {
      if (className.equals(registry.getBeanDefinition(name).getBeanClassName())) {
        return true;
      }
    }
    if (registry instanceof ListableBeanFactory beanFactory) {
      Class<?> type = ClassUtils.resolveClassName(className, classLoader);
      return beanFactory.getBeanNamesForType(type, true, false).length > 0;
    }
    return false;
  }
}
