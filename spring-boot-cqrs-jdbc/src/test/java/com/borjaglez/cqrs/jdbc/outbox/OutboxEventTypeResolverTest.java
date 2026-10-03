package com.borjaglez.cqrs.jdbc.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.fixtures.TestOrderPlaced;
import com.borjaglez.cqrs.fixtures.TestOrderPlacedHandler;
import com.borjaglez.cqrs.fixtures.outbox.TestAnnotatedNotAnEvent;
import com.borjaglez.cqrs.fixtures.outbox.TestRenamedEvent;
import com.borjaglez.cqrs.fixtures.outbox.ambiguous.TestFirstShared;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;

class OutboxEventTypeResolverTest {

  private static final String FIXTURES = "com.borjaglez.cqrs.fixtures.outbox";
  private static final String AMBIGUOUS = "com.borjaglez.cqrs.fixtures.outbox.ambiguous";

  private final MessageNamingStrategy naming = new DefaultMessageNamingStrategy("");
  private final EventHandlerRegistry registry = new EventHandlerRegistry();
  private final ClassLoader classLoader = getClass().getClassLoader();

  private OutboxEventTypeResolver resolver(Set<String> packages, ClassLoader loader) {
    return new OutboxEventTypeResolver(registry, naming, packages, loader);
  }

  @Test
  void resolvesAHandledEventByItsLogicalName() throws Exception {
    registry.register(
        TestOrderPlaced.class,
        new TestOrderPlacedHandler(),
        TestOrderPlacedHandler.class.getMethod("on", TestOrderPlaced.class),
        "order-placed-v2");

    assertThat(resolver(Set.of(), classLoader).resolve("order-placed-v2", "com.example.Gone"))
        .isEqualTo(TestOrderPlaced.class);
  }

  @Test
  void resolvesAnEventOfTheScannedPackagesByItsLogicalName() {
    OutboxEventTypeResolver resolver = resolver(Set.of(FIXTURES), classLoader);
    String name = naming.eventName(TestRenamedEvent.class);

    assertThat(resolver.resolve(name, "com.example.OldName")).isEqualTo(TestRenamedEvent.class);
    assertThat(resolver.resolve(name, "com.example.OldName")).isEqualTo(TestRenamedEvent.class);
  }

  @Test
  void fallsBackToTheStoredClassName() {
    assertThat(
            resolver(Set.of(FIXTURES), classLoader)
                .resolve("unknown", TestOrderPlaced.class.getName()))
        .isEqualTo(TestOrderPlaced.class);
  }

  @Test
  void annotatedClassesThatAreNotEventsAreNotIndexed() {
    String name = naming.eventName(TestAnnotatedNotAnEvent.class);

    assertThat(
            resolver(Set.of(FIXTURES), classLoader).resolve(name, TestOrderPlaced.class.getName()))
        .isEqualTo(TestOrderPlaced.class);
  }

  @Test
  void ambiguousNamesFallBackToTheStoredClassName() {
    String name = naming.eventName(TestFirstShared.class);

    assertThat(
            resolver(Set.of(AMBIGUOUS), classLoader).resolve(name, TestOrderPlaced.class.getName()))
        .isEqualTo(TestOrderPlaced.class);
  }

  @Test
  void overlappingPackagesIndexAClassOnce() {
    OutboxEventTypeResolver resolver =
        resolver(Set.of("com.borjaglez.cqrs.fixtures", FIXTURES), classLoader);

    assertThat(resolver.resolve(naming.eventName(TestRenamedEvent.class), "com.example.OldName"))
        .isEqualTo(TestRenamedEvent.class);
  }

  @Test
  void unknownNameAndClassAreReported() {
    assertThatThrownBy(() -> resolver(Set.of(), classLoader).resolve("unknown", "com.example.Gone"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown")
        .hasMessageContaining("com.example.Gone")
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void aClassThatIsNotAnEventIsReported() {
    assertThatThrownBy(() -> resolver(Set.of(), classLoader).resolve("x", String.class.getName()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not an Event");
  }

  @Test
  void theGeneratedIndexIsPreferredOverScanning() {
    ClassLoader loader =
        withIndex("order-x=" + TestOrderPlaced.class.getName() + "\n", classLoader);
    OutboxEventTypeResolver resolver = resolver(Set.of(FIXTURES), loader);

    assertThat(resolver.resolve("order-x", "com.example.Gone")).isEqualTo(TestOrderPlaced.class);
    assertThatThrownBy(
            () -> resolver.resolve(naming.eventName(TestRenamedEvent.class), "com.example.Gone"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anUnreadableGeneratedIndexIsReported() {
    ClassLoader loader =
        new ClassLoader(classLoader) {
          @Override
          public InputStream getResourceAsStream(String name) {
            return new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException("unreadable");
              }
            };
          }
        };

    assertThatThrownBy(() -> resolver(Set.of(), loader).resolve("x", "com.example.Gone"))
        .isInstanceOf(UncheckedIOException.class)
        .hasRootCauseMessage("unreadable");
  }

  @Test
  void anIndexThatFailsToReadAndToCloseIsReported() {
    ClassLoader loader =
        new ClassLoader(classLoader) {
          @Override
          public InputStream getResourceAsStream(String name) {
            return new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException("unreadable");
              }

              @Override
              public void close() throws IOException {
                throw new IOException("not closable");
              }
            };
          }
        };

    assertThatThrownBy(() -> resolver(Set.of(), loader).resolve("x", "com.example.Gone"))
        .isInstanceOf(UncheckedIOException.class)
        .hasRootCauseMessage("unreadable")
        .cause()
        .hasSuppressedException(new IOException("not closable"));
  }

  @Test
  void anIndexThatFailsToCloseIsReported() {
    ClassLoader loader =
        new ClassLoader(classLoader) {
          @Override
          public InputStream getResourceAsStream(String name) {
            return new ByteArrayInputStream(new byte[0]) {
              @Override
              public void close() throws IOException {
                throw new IOException("not closable");
              }
            };
          }
        };

    assertThatThrownBy(() -> resolver(Set.of(), loader).resolve("x", "com.example.Gone"))
        .isInstanceOf(UncheckedIOException.class)
        .hasRootCauseMessage("not closable");
  }

  @Test
  void packagesAreReadAsAListOrACommaSeparatedValue() {
    assertThat(
            OutboxEventTypeResolver.packages(
                new MockEnvironment().withProperty("cqrs.aot.message-packages", "a.b, c.d")))
        .containsExactlyInAnyOrder("a.b", "c.d");
    assertThat(
            OutboxEventTypeResolver.packages(
                new MockEnvironment()
                    .withProperty("cqrs.aot.message-packages[0]", "a.b")
                    .withProperty("cqrs.aot.message-packages[1]", "c.d")))
        .containsExactlyInAnyOrder("a.b", "c.d");
    assertThat(OutboxEventTypeResolver.packages(new MockEnvironment())).isEmpty();
  }

  private static ClassLoader withIndex(String content, ClassLoader parent) {
    return new ClassLoader(parent) {
      @Override
      public InputStream getResourceAsStream(String name) {
        if (OutboxEventTypeResolver.INDEX_LOCATION.equals(name)) {
          return new ByteArrayInputStream(content.getBytes(UTF_8));
        }
        return super.getResourceAsStream(name);
      }
    };
  }
}
