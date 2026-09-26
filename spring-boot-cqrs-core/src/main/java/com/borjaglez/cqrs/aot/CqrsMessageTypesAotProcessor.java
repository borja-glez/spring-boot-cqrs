package com.borjaglez.cqrs.aot;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Registers binding hints for the types an application sends or receives without declaring them in
 * a handler: a command sent to another service, the reply it gets back, an event stored in an
 * outbox. Handler parameters and results are covered by {@link CqrsBeanRegistrationAotProcessor};
 * these types can only be found by scanning, so list the packages that hold them (usually the
 * shared contracts) in {@value #PACKAGES_PROPERTY}, comma separated. Every concrete type in those
 * packages is registered: a reply is a plain record, not a message, and nothing says which command
 * it answers.
 */
public class CqrsMessageTypesAotProcessor implements BeanFactoryInitializationAotProcessor {

  public static final String PACKAGES_PROPERTY = "cqrs.aot.message-packages";

  private final BindingReflectionHintsRegistrar bindings = new BindingReflectionHintsRegistrar();

  @Override
  public BeanFactoryInitializationAotContribution processAheadOfTime(
      ConfigurableListableBeanFactory beanFactory) {
    Set<String> packages = packages(beanFactory);
    if (packages.isEmpty()) {
      return null;
    }
    Set<Class<?>> types = scan(packages, beanFactory.getBeanClassLoader());
    return (generationContext, code) -> registerHints(generationContext.getRuntimeHints(), types);
  }

  void registerHints(RuntimeHints hints, Set<Class<?>> types) {
    types.forEach(type -> bindings.registerReflectionHints(hints.reflection(), type));
  }

  private static Set<String> packages(ConfigurableListableBeanFactory beanFactory) {
    if (!beanFactory.containsBean("environment")) {
      return Set.of();
    }
    String value =
        beanFactory.getBean("environment", Environment.class).getProperty(PACKAGES_PROPERTY);
    return StringUtils.commaDelimitedListToSet(value).stream()
        .map(String::trim)
        .filter(StringUtils::hasText)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  static Set<Class<?>> scan(Set<String> packages, ClassLoader classLoader) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter((reader, factory) -> true);
    Set<Class<?>> types = new LinkedHashSet<>();
    for (String basePackage : packages) {
      scanner
          .findCandidateComponents(basePackage)
          .forEach(
              candidate ->
                  types.add(
                      ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader)));
    }
    return types;
  }
}
