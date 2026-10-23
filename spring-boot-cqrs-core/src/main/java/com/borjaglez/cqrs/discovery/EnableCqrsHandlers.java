package com.borjaglez.cqrs.discovery;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Registers the handler classes of some packages as beans, for applications without Spring Boot.
 * With a Spring Boot starter the handlers of the application packages are registered automatically
 * and this annotation is not needed.
 *
 * <p>Every concrete class of the packages, or of their sub-packages, annotated with a handler
 * annotation ({@code @CommandHandler}, {@code @QueryHandler}, {@code @EventHandler}) becomes a bean
 * unless the context already holds one of its class (see {@link HandlerTypeScanner}). Without
 * {@link #basePackages()} and {@link #basePackageClasses()}, the package of the annotated class is
 * scanned.
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableCqrsHandlers(basePackages = "com.example.orders")
 * class CqrsConfiguration {}
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EnableCqrsHandlersRegistrar.class)
public @interface EnableCqrsHandlers {

  /**
   * Packages to scan for handler classes.
   *
   * @return the base packages
   */
  String[] basePackages() default {};

  /**
   * Classes whose packages are scanned for handler classes; a type-safe alternative to {@link
   * #basePackages()}.
   *
   * @return the classes that mark the base packages
   */
  Class<?>[] basePackageClasses() default {};
}
