package com.borjaglez.cqrs.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.borjaglez.cqrs.aot.scanned.CardCharged;
import com.borjaglez.cqrs.aot.scanned.ChargeCard;
import com.borjaglez.cqrs.aot.scanned.OrderLine;
import com.borjaglez.cqrs.aot.scanned.OrderPlaced;
import com.borjaglez.cqrs.aot.scanned.Tagged;

class CqrsMessageTypesAotProcessorTest {

  private static final String PACKAGE = "com.borjaglez.cqrs.aot.scanned";

  private final CqrsMessageTypesAotProcessor processor = new CqrsMessageTypesAotProcessor();

  private static DefaultListableBeanFactory beanFactory(String packages) {
    return beanFactoryWith(
        packages == null
            ? Map.of()
            : Map.of(CqrsMessageTypesAotProcessor.PACKAGES_PROPERTY, packages));
  }

  private static DefaultListableBeanFactory beanFactoryWith(Map<String, Object> properties) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    beanFactory.registerSingleton("environment", environment);
    return beanFactory;
  }

  private static RuntimeHints apply(BeanFactoryInitializationAotContribution contribution) {
    RuntimeHints hints = new RuntimeHints();
    GenerationContext context = mock(GenerationContext.class);
    when(context.getRuntimeHints()).thenReturn(hints);
    contribution.applyTo(context, mock(BeanFactoryInitializationCode.class));
    return hints;
  }

  @Test
  void findsEveryTypeOfTheConfiguredPackagesOnly() {
    Set<Class<?>> types =
        CqrsMessageTypesAotProcessor.scan(Set.of(PACKAGE), getClass().getClassLoader());

    assertThat(types)
        .contains(
            OrderPlaced.class, ChargeCard.class, Tagged.class, OrderLine.class, CardCharged.class)
        .doesNotContain(Unlisted.class, CqrsMessageTypesAotProcessorTest.class);
  }

  @Test
  void registersBindingHintsForSentAndRecordedMessagesAndWhatTheyContain() {
    BeanFactoryInitializationAotContribution contribution =
        processor.processAheadOfTime(beanFactory(" " + PACKAGE + " , "));

    RuntimeHints hints = apply(contribution);

    assertThat(RuntimeHintsPredicates.reflection().onType(OrderPlaced.class)).accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(ChargeCard.class)).accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(OrderLine.class)).accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onMethod(CardCharged.class, "charged").invoke())
        .accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onMethod(OrderLine.class, "sku").invoke())
        .accepts(hints);
  }

  @Test
  void doesNothingWithoutPackages() {
    assertThat(processor.processAheadOfTime(beanFactory(null))).isNull();
    assertThat(processor.processAheadOfTime(beanFactory(" , "))).isNull();
    assertThat(processor.processAheadOfTime(new DefaultListableBeanFactory())).isNull();
  }

  @Test
  void acceptsThePackagesAsAYamlList() {
    BeanFactoryInitializationAotContribution contribution =
        processor.processAheadOfTime(
            beanFactoryWith(
                Map.of(
                    CqrsMessageTypesAotProcessor.PACKAGES_PROPERTY + "[0]",
                    " ",
                    CqrsMessageTypesAotProcessor.PACKAGES_PROPERTY + "[1]",
                    PACKAGE)));

    assertThat(RuntimeHintsPredicates.reflection().onType(ChargeCard.class))
        .accepts(apply(contribution));
  }
}
