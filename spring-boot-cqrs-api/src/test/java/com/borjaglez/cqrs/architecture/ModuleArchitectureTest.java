package com.borjaglez.cqrs.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

  // lombok.Generated is added by Lombok (bytecode annotation, see
  // lombok.addLombokGeneratedAnnotation)
  // to generated methods so that JaCoCo ignores them; it is not a runtime dependency.
  @ArchTest
  static final ArchRule apiDependsOnlyOnJdkAndOwnPackages =
      classes()
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "com.borjaglez.cqrs..", "lombok");

  @ArchTest
  static final ArchRule noFieldInjection =
      noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");
}
