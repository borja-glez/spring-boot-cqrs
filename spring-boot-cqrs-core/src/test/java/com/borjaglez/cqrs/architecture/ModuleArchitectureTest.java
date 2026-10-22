package com.borjaglez.cqrs.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.LocationProvider;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the module boundaries of this module's production classes. */
@AnalyzeClasses(
    locations = ModuleArchitectureTest.ModuleMainClasses.class,
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

  /** Resolves only this module's main class output, excluding api/core jars on the classpath. */
  static final class ModuleMainClasses implements LocationProvider {
    @Override
    public Set<Location> get(Class<?> testClass) {
      try {
        Path testClasses =
            Path.of(testClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Set.of(Location.of(testClasses.resolveSibling("main")));
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @ArchTest
  static final ArchRule coreHasNoBootOrAdapterDependencies =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.boot..",
              "com.borjaglez.cqrs.kafka..",
              "com.borjaglez.cqrs.rabbitmq..",
              "com.borjaglez.cqrs.jdbc..",
              "com.borjaglez.cqrs.autoconfigure..",
              "com.borjaglez.cqrs.actuator..",
              "com.borjaglez.cqrs.test..",
              "com.borjaglez.cqrs.asyncapi..");

  @ArchTest
  static final ArchRule noFieldInjection =
      noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");
}
