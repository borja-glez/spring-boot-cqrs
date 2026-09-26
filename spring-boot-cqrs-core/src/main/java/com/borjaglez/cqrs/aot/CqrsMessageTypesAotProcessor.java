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
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

/**
 * Registers binding hints for the messages an application only sends or records, which no local
 * handler declares: a command sent to another service, an event stored in an outbox. Handler
 * parameters are covered by {@link CqrsBeanRegistrationAotProcessor}; these types can only be found
 * by scanning, so list their packages in {@value #PACKAGES_PROPERTY} (comma separated).
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
    scanner.addIncludeFilter(new AssignableTypeFilter(Command.class));
    scanner.addIncludeFilter(new AssignableTypeFilter(Event.class));
    scanner.addIncludeFilter(new AssignableTypeFilter(Query.class));
    scanner.addIncludeFilter(new AnnotationTypeFilter(CqrsMessage.class));
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
