package com.borjaglez.cqrs.test.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

class CqrsArchitectureRulesTest {

  private static final String FIXTURES = "com.borjaglez.cqrs.test.architecture.fixtures.";

  private static final JavaClasses GOOD = new ClassFileImporter().importPackages(FIXTURES + "good");
  private static final JavaClasses BAD = new ClassFileImporter().importPackages(FIXTURES + "bad");

  @Test
  void fixturesAreImported() {
    assertThat(GOOD).hasSize(6);
    assertThat(BAD).hasSize(8);
  }

  @Test
  void handlersDoNotDependOnSpringPassesOnGoodFixtures() {
    CqrsArchitectureRules.handlersDoNotDependOnSpring().check(GOOD);
  }

  @Test
  void handlersDoNotDependOnSpringReportsViolations() {
    assertViolation(
        CqrsArchitectureRules.handlersDoNotDependOnSpring(), "SpringDependentCommandHandler");
  }

  @Test
  void handlersDoNotDependOnSpringFlagsSpringFieldsAndAnnotations() {
    assertViolation(
        CqrsArchitectureRules.handlersDoNotDependOnSpring(), "AnnotatedSpringEventHandler");
  }

  @Test
  void userDefinedAbstractMessageBaseIsIgnoredButItsSubclassIsChecked() {
    assertThatThrownBy(() -> CqrsArchitectureRules.messagesAreAnnotatedWithCqrsMessage().check(BAD))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ConcreteOrderCommand")
        .hasMessageNotContaining("AbstractOrderCommand");
  }

  @Test
  void handlerMethodsAreInHandlerClassesPassesOnGoodFixtures() {
    CqrsArchitectureRules.handlerMethodsAreInHandlerClasses().check(GOOD);
  }

  @Test
  void handlerMethodsAreInHandlerClassesReportsViolations() {
    assertViolation(
        CqrsArchitectureRules.handlerMethodsAreInHandlerClasses(), "MisplacedQueryHandlerMethod");
  }

  @Test
  void messagesAreAnnotatedWithCqrsMessagePassesOnGoodFixtures() {
    CqrsArchitectureRules.messagesAreAnnotatedWithCqrsMessage().check(GOOD);
  }

  @Test
  void messagesAreAnnotatedWithCqrsMessageReportsViolations() {
    assertViolation(
        CqrsArchitectureRules.messagesAreAnnotatedWithCqrsMessage(), "NoAnnotationCommand");
  }

  @Test
  void messagesHaveANoArgConstructorPassesOnGoodFixtures() {
    CqrsArchitectureRules.messagesHaveANoArgConstructor().check(GOOD);
  }

  @Test
  void messagesHaveANoArgConstructorReportsViolations() {
    assertViolation(
        CqrsArchitectureRules.messagesHaveANoArgConstructor(), "NoDefaultConstructorQuery");
  }

  @Test
  void messagesDoNotDependOnFrameworksPassesOnGoodFixtures() {
    CqrsArchitectureRules.messagesDoNotDependOnFrameworks().check(GOOD);
  }

  @Test
  void messagesDoNotDependOnFrameworksReportsViolations() {
    assertViolation(
        CqrsArchitectureRules.messagesDoNotDependOnFrameworks(), "SpringDependentEvent");
  }

  @Test
  void rulesIgnoreAbstractMessageBaseClasses() {
    JavaClasses base =
        new ClassFileImporter()
            .importClasses(
                com.borjaglez.cqrs.command.Command.class,
                com.borjaglez.cqrs.query.Query.class,
                com.borjaglez.cqrs.event.Event.class);

    CqrsArchitectureRules.messagesAreAnnotatedWithCqrsMessage().allowEmptyShould(true).check(base);
    CqrsArchitectureRules.messagesHaveANoArgConstructor().allowEmptyShould(true).check(base);
    CqrsArchitectureRules.messagesDoNotDependOnFrameworks().allowEmptyShould(true).check(base);
  }

  private static void assertViolation(ArchRule rule, String offendingClass) {
    assertThatThrownBy(() -> rule.check(BAD))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(offendingClass);
  }
}
