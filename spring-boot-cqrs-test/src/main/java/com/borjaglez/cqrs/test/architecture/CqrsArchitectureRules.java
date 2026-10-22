package com.borjaglez.cqrs.test.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.lang.annotation.Annotation;
import java.util.Map;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasModifiers;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Reusable ArchUnit rules for applications that use spring-boot-cqrs.
 *
 * <p>"Messages" are the concrete classes assignable to {@link Command}, {@link Query} or {@link
 * Event}; the abstract base classes themselves are never reported. ArchUnit is a {@code
 * compileOnly} dependency of this module: add it to your own test classpath.
 */
public final class CqrsArchitectureRules {

  private static final String[] FRAMEWORK_PACKAGES = {
    "org.springframework..",
    "com.fasterxml.jackson.databind..",
    "tools.jackson..",
    "jakarta.persistence..",
    "org.hibernate.."
  };

  private static final Map<Class<? extends Annotation>, Class<? extends Annotation>>
      HANDLER_CLASS_BY_METHOD_ANNOTATION =
          Map.of(
              HandleCommand.class, CommandHandler.class,
              HandleQuery.class, QueryHandler.class,
              HandleEvent.class, EventHandler.class);

  private static final DescribedPredicate<JavaClass> CONCRETE_MESSAGES =
      JavaClass.Predicates.assignableTo(Command.class)
          .or(JavaClass.Predicates.assignableTo(Query.class))
          .or(JavaClass.Predicates.assignableTo(Event.class))
          .and(DescribedPredicate.not(JavaClass.Predicates.INTERFACES))
          .and(DescribedPredicate.not(HasModifiers.Predicates.modifier(JavaModifier.ABSTRACT)))
          .as("concrete messages (Command, Query or Event)");

  private CqrsArchitectureRules() {}

  /**
   * Handler classes, including those carrying a composed annotation meta-annotated with a handler
   * annotation, must stay free of Spring so they can be unit tested without a container.
   */
  public static ArchRule handlersDoNotDependOnSpring() {
    return noClasses()
        .that()
        .areMetaAnnotatedWith(CommandHandler.class)
        .or()
        .areMetaAnnotatedWith(QueryHandler.class)
        .or()
        .areMetaAnnotatedWith(EventHandler.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .because("handlers hold business logic and must be testable without Spring");
  }

  /**
   * Handler methods are only discovered on classes carrying the matching class annotation, directly
   * or as a meta-annotation.
   */
  public static ArchRule handlerMethodsAreInHandlerClasses() {
    return methods()
        .that(
            new DescribedPredicate<JavaMethod>("are annotated with a CQRS handle annotation") {
              @Override
              public boolean test(JavaMethod method) {
                return HANDLER_CLASS_BY_METHOD_ANNOTATION.keySet().stream()
                    .anyMatch(method::isAnnotatedWith);
              }
            })
        .should(
            new ArchCondition<JavaMethod>(
                "be declared in a class with the matching handler annotation") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                HANDLER_CLASS_BY_METHOD_ANNOTATION.forEach(
                    (methodAnnotation, classAnnotation) -> {
                      if (method.isAnnotatedWith(methodAnnotation)
                          && !method.getOwner().isMetaAnnotatedWith(classAnnotation)) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                method.getFullName()
                                    + " is annotated with @"
                                    + methodAnnotation.getSimpleName()
                                    + " but "
                                    + method.getOwner().getName()
                                    + " is not annotated with @"
                                    + classAnnotation.getSimpleName()));
                      }
                    });
              }
            })
        .because("handler methods are only discovered on classes with the handler annotation");
  }

  /** Every concrete message needs {@link CqrsMessage} to get a stable routing identity. */
  public static ArchRule messagesAreAnnotatedWithCqrsMessage() {
    return classes()
        .that(CONCRETE_MESSAGES)
        .should()
        .beAnnotatedWith(CqrsMessage.class)
        .because("transport adapters derive routing keys from @CqrsMessage");
  }

  /** Concrete messages need a constructor without arguments. */
  public static ArchRule messagesHaveANoArgConstructor() {
    return classes()
        .that(CONCRETE_MESSAGES)
        .should(
            new ArchCondition<JavaClass>("have a no-arg constructor") {
              @Override
              public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasNoArg =
                    javaClass.getConstructors().stream()
                        .anyMatch(c -> c.getRawParameterTypes().isEmpty());
                if (!hasNoArg) {
                  events.add(
                      SimpleConditionEvent.violated(
                          javaClass, javaClass.getName() + " has no no-arg constructor"));
                }
              }
            })
        .because("serializers instantiate messages through a no-arg constructor");
  }

  /**
   * Messages are plain data and must not depend on Spring ({@code org.springframework..}), Jackson
   * databind in versions 2 and 3 ({@code com.fasterxml.jackson.databind..}, {@code
   * tools.jackson..}), JPA ({@code jakarta.persistence..}) or Hibernate ({@code org.hibernate..}).
   * Jackson annotations ({@code com.fasterxml.jackson.annotation..}) are allowed.
   */
  public static ArchRule messagesDoNotDependOnFrameworks() {
    return noClasses()
        .that(CONCRETE_MESSAGES)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(FRAMEWORK_PACKAGES)
        .because("messages are plain data contracts that must not be coupled to frameworks");
  }
}
