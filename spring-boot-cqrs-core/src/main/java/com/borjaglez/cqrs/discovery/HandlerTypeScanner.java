package com.borjaglez.cqrs.discovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

/**
 * Registers a bean for every handler class found in some packages, so that a class annotated with
 * {@link CommandHandler @CommandHandler}, {@link QueryHandler @QueryHandler} or {@link
 * EventHandler @EventHandler} becomes a handler without being a component itself.
 *
 * <p>The candidates are the concrete, independent classes of the packages (and their sub-packages)
 * annotated, directly or as a meta-annotation, with one of the three handler annotations, and not
 * matched by an {@linkplain #addExcludeFilter exclude filter}. A candidate is skipped when the
 * registry already holds it: a bean definition with its class name (a component scan, an explicit
 * registration) or, when the registry has a bean factory, a bean of exactly its class, such as one
 * declared by a {@code @Bean} method; a bean of a subclass does not count. It is also skipped when
 * one of its conditions does not match: {@code @Conditional}, {@code @Profile} or the conditions of
 * Spring Boot, evaluated against the registry and its bean factory.
 *
 * <p>Bean names come from {@link AnnotationBeanNameGenerator}; a name already used by a bean
 * definition of another class fails with an {@link IllegalStateException}. The definitions honour
 * the annotations a component scan honours, such as {@code @Scope}, {@code @Lazy}, {@code @Primary}
 * and {@code @DependsOn}.
 */
public final class HandlerTypeScanner {

  private static final List<TypeFilter> HANDLER_FILTERS =
      List.of(
          new AnnotationTypeFilter(CommandHandler.class),
          new AnnotationTypeFilter(QueryHandler.class),
          new AnnotationTypeFilter(EventHandler.class));

  private final Environment environment;
  private final ResourceLoader resourceLoader;
  private final List<TypeFilter> excludeFilters = new ArrayList<>();

  public HandlerTypeScanner(Environment environment, ResourceLoader resourceLoader) {
    this.environment = environment;
    this.resourceLoader = resourceLoader;
  }

  /**
   * Leaves out the candidates {@code excludeFilter} matches, as an exclude filter of a component
   * scan does.
   */
  public void addExcludeFilter(TypeFilter excludeFilter) {
    excludeFilters.add(excludeFilter);
  }

  /**
   * Registers the handler classes of {@code basePackages} that {@code registry} does not hold yet.
   *
   * @return the names of the beans registered, in registration order
   * @throws IllegalStateException if the bean name of a handler is used by another bean definition
   */
  public Set<String> register(BeanDefinitionRegistry registry, Collection<String> basePackages) {
    return new Scanner(registry).registerHandlers(basePackages);
  }

  /**
   * A scan bound to one registry. It is also the context its conditions see: it already exposes the
   * registry, the environment and the resource loader.
   */
  private final class Scanner extends ClassPathBeanDefinitionScanner implements ConditionContext {

    private final ConfigurableListableBeanFactory beanFactory;

    Scanner(BeanDefinitionRegistry registry) {
      super(registry, false, environment, resourceLoader);
      // Unused by isCandidateComponent below, but they stop a components index from being used,
      // as the handler annotations are not indexed.
      HANDLER_FILTERS.forEach(this::addIncludeFilter);
      this.beanFactory = beanFactoryOf(registry);
    }

    Set<String> registerHandlers(Collection<String> basePackages) {
      Set<String> registered = new LinkedHashSet<>();
      for (String basePackage : basePackages) {
        for (BeanDefinitionHolder holder : doScan(basePackage)) {
          registered.add(holder.getBeanName());
        }
      }
      return registered;
    }

    /**
     * Applies the filters only. The conditions are evaluated in {@link #checkCandidate}, once the
     * handler is known to be missing, and all of them; for a handler that is also a component the
     * scanner's own evaluation would run only the conditions of the configuration parsing phase,
     * leaving out the bean conditions of Spring Boot.
     */
    @Override
    protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
      for (TypeFilter excludeFilter : excludeFilters) {
        if (excludeFilter.match(metadataReader, getMetadataReaderFactory())) {
          return false;
        }
      }
      for (TypeFilter handlerFilter : HANDLER_FILTERS) {
        if (handlerFilter.match(metadataReader, getMetadataReaderFactory())) {
          return true;
        }
      }
      return false;
    }

    @Override
    protected boolean checkCandidate(String beanName, BeanDefinition candidate) {
      String className = candidate.getBeanClassName();
      if (isRegistered(className)
          || !conditionsMatch(((AnnotatedBeanDefinition) candidate).getMetadata())) {
        return false;
      }
      if (getRegistry().containsBeanDefinition(beanName)) {
        throw new IllegalStateException(
            "Bean name '"
                + beanName
                + "' of handler class ["
                + className
                + "] is already used by another bean definition: "
                + getRegistry().getBeanDefinition(beanName)
                + ". Rename one of the classes or register the handler yourself under another"
                + " name");
      }
      return true;
    }

    private boolean isRegistered(String className) {
      BeanDefinitionRegistry registry = getRegistry();
      for (String name : registry.getBeanDefinitionNames()) {
        if (className.equals(registry.getBeanDefinition(name).getBeanClassName())) {
          return true;
        }
      }
      if (beanFactory == null) {
        return false;
      }
      Class<?> type = ClassUtils.resolveClassName(className, getClassLoader());
      for (String name : beanFactory.getBeanNamesForType(type, true, false)) {
        if (ClassUtils.getUserClass(beanFactory.getType(name, false)) == type) {
          return true;
        }
      }
      return false;
    }

    private boolean conditionsMatch(AnnotationMetadata metadata) {
      MultiValueMap<String, Object> conditional =
          metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
      if (conditional == null) {
        return true;
      }
      List<Condition> conditions = new ArrayList<>();
      for (Object conditionClasses : conditional.get("value")) {
        for (String conditionClass : (String[]) conditionClasses) {
          conditions.add(
              BeanUtils.instantiateClass(
                  ClassUtils.resolveClassName(conditionClass, getClassLoader()), Condition.class));
        }
      }
      AnnotationAwareOrderComparator.sort(conditions);
      return conditions.stream().allMatch(condition -> condition.matches(this, metadata));
    }

    @Override
    public ConfigurableListableBeanFactory getBeanFactory() {
      return beanFactory;
    }

    @Override
    public ClassLoader getClassLoader() {
      return getResourceLoader().getClassLoader();
    }
  }

  private static ConfigurableListableBeanFactory beanFactoryOf(BeanDefinitionRegistry registry) {
    if (registry instanceof ConfigurableListableBeanFactory beanFactory) {
      return beanFactory;
    }
    if (registry instanceof ConfigurableApplicationContext context) {
      return context.getBeanFactory();
    }
    return null;
  }
}
