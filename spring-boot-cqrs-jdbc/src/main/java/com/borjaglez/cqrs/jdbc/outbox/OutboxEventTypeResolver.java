package com.borjaglez.cqrs.jdbc.outbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.borjaglez.cqrs.aot.CqrsMessageTypesAotProcessor;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;

/**
 * Finds the class of a stored outbox event. The logical name is tried first, so a class annotated
 * with {@link CqrsMessage} may be renamed or moved while rows are pending: among the events this
 * application handles, then among the {@code @CqrsMessage} events of the packages listed in {@code
 * cqrs.aot.message-packages}. The stored class name is the fallback.
 *
 * <p>The package index is built on first use by scanning the classpath, or read from {@value
 * #INDEX_LOCATION} when {@link OutboxEventNamesAotProcessor} generated it (native images cannot
 * scan). A name shared by two classes is ambiguous and falls back to the stored class name.
 */
public class OutboxEventTypeResolver {

  public static final String INDEX_LOCATION = "META-INF/cqrs/outbox-event-names.properties";

  private static final String AMBIGUOUS = "";

  private final EventHandlerRegistry registry;
  private final MessageNamingStrategy naming;
  private final Set<String> packages;
  private final ClassLoader classLoader;
  private volatile Map<String, String> index;

  public OutboxEventTypeResolver(
      EventHandlerRegistry registry,
      MessageNamingStrategy naming,
      Set<String> packages,
      ClassLoader classLoader) {
    this.registry = registry;
    this.naming = naming;
    this.packages = Set.copyOf(packages);
    this.classLoader = classLoader;
  }

  public Class<? extends Event> resolve(String eventName, String eventClass) {
    Class<?> type =
        registry.findMessageClass(eventName).orElseGet(() -> load(eventName, eventClass));
    if (!Event.class.isAssignableFrom(type)) {
      throw new IllegalStateException(
          type.getName() + ", stored as outbox event '" + eventName + "', is not an Event");
    }
    return type.asSubclass(Event.class);
  }

  private Class<?> load(String eventName, String eventClass) {
    String indexed = index().get(eventName);
    String className = indexed == null || indexed.equals(AMBIGUOUS) ? eventClass : indexed;
    try {
      return ClassUtils.forName(className, classLoader);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Cannot resolve outbox event '"
              + eventName
              + "': no local @CqrsMessage event has this name and class "
              + eventClass
              + " does not exist here",
          e);
    }
  }

  private Map<String, String> index() {
    Map<String, String> current = index;
    if (current == null) {
      Map<String, String> generated = readGeneratedIndex(classLoader);
      current = generated != null ? generated : scan(packages, naming, classLoader);
      index = current;
    }
    return current;
  }

  /** The packages of {@code cqrs.aot.message-packages}, written as a list or comma separated. */
  public static Set<String> packages(Environment environment) {
    Set<String> values =
        Binder.get(environment)
            .bind(CqrsMessageTypesAotProcessor.PACKAGES_PROPERTY, Bindable.setOf(String.class))
            .orElse(Set.of());
    return values.stream()
        .map(String::trim)
        .filter(StringUtils::hasText)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Logical name to class name of the {@code @CqrsMessage} events found in {@code packages}. */
  public static Map<String, String> scan(
      Set<String> packages, MessageNamingStrategy naming, ClassLoader classLoader) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.setResourceLoader(new DefaultResourceLoader(classLoader));
    scanner.addIncludeFilter(new AnnotationTypeFilter(CqrsMessage.class));
    Map<String, String> names = new HashMap<>();
    for (String basePackage : packages) {
      for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
        Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader);
        if (Event.class.isAssignableFrom(type)) {
          names.merge(
              naming.eventName(type),
              type.getName(),
              (existing, added) -> existing.equals(added) ? existing : AMBIGUOUS);
        }
      }
    }
    return names;
  }

  private static Map<String, String> readGeneratedIndex(ClassLoader classLoader) {
    InputStream in = classLoader.getResourceAsStream(INDEX_LOCATION);
    if (in == null) {
      return null;
    }
    Properties properties = new Properties();
    IOException failure = null;
    try {
      properties.load(in);
    } catch (IOException e) {
      failure = e;
    }
    try {
      in.close();
    } catch (IOException e) {
      if (failure == null) {
        failure = e;
      } else {
        failure.addSuppressed(e);
      }
    }
    if (failure != null) {
      throw new UncheckedIOException(failure);
    }
    Map<String, String> names = new HashMap<>();
    properties.stringPropertyNames().forEach(name -> names.put(name, properties.getProperty(name)));
    return names;
  }
}
