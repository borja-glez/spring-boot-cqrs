package com.borjaglez.cqrs.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.support.RegisteredBean;

import com.borjaglez.cqrs.fixtures.TestCommandHandler;
import com.borjaglez.cqrs.fixtures.TestEventHandler;
import com.borjaglez.cqrs.fixtures.TestQueryHandler;

@ExtendWith(MockitoExtension.class)
class CqrsBeanRegistrationAotProcessorTest {

  private final CqrsBeanRegistrationAotProcessor processor = new CqrsBeanRegistrationAotProcessor();

  @Test
  void returnsNullForNonHandlerBean() {
    RegisteredBean registeredBean = mock(RegisteredBean.class);
    when(registeredBean.getBeanClass()).thenReturn((Class) String.class);

    BeanRegistrationAotContribution result = processor.processAheadOfTime(registeredBean);
    assertThat(result).isNull();
  }

  @Test
  void returnsContributionForCommandHandlerBean() {
    RegisteredBean registeredBean = mock(RegisteredBean.class);
    when(registeredBean.getBeanClass()).thenReturn((Class) TestCommandHandler.class);

    BeanRegistrationAotContribution result = processor.processAheadOfTime(registeredBean);
    assertThat(result).isNotNull();
  }

  @Test
  void returnsContributionForEventHandlerBean() {
    RegisteredBean registeredBean = mock(RegisteredBean.class);
    when(registeredBean.getBeanClass()).thenReturn((Class) TestEventHandler.class);

    BeanRegistrationAotContribution result = processor.processAheadOfTime(registeredBean);
    assertThat(result).isNotNull();
  }

  @Test
  void returnsContributionForQueryHandlerBean() {
    RegisteredBean registeredBean = mock(RegisteredBean.class);
    when(registeredBean.getBeanClass()).thenReturn((Class) TestQueryHandler.class);

    BeanRegistrationAotContribution result = processor.processAheadOfTime(registeredBean);
    assertThat(result).isNotNull();
  }

  @Test
  void contributionRegistersRuntimeHintsForCommandHandler() throws Exception {
    RegisteredBean registeredBean = mock(RegisteredBean.class);
    when(registeredBean.getBeanClass()).thenReturn((Class) TestCommandHandler.class);

    BeanRegistrationAotContribution contribution = processor.processAheadOfTime(registeredBean);
    assertThat(contribution).isNotNull();

    // Execute the contribution to verify hints registration
    org.springframework.aot.generate.GenerationContext generationContext =
        mock(org.springframework.aot.generate.GenerationContext.class);
    RuntimeHints hints = new RuntimeHints();
    when(generationContext.getRuntimeHints()).thenReturn(hints);

    org.springframework.beans.factory.aot.BeanRegistrationCode beanRegistrationCode =
        mock(org.springframework.beans.factory.aot.BeanRegistrationCode.class);

    contribution.applyTo(generationContext, beanRegistrationCode);

    assertThat(RuntimeHintsPredicates.reflection().onType(TestCommandHandler.class).test(hints))
        .isTrue();
  }

  @Test
  void contributionRegistersRuntimeHintsForEventHandler() throws Exception {
    RegisteredBean registeredBean = mock(RegisteredBean.class);
    when(registeredBean.getBeanClass()).thenReturn((Class) TestEventHandler.class);

    BeanRegistrationAotContribution contribution = processor.processAheadOfTime(registeredBean);
    assertThat(contribution).isNotNull();

    org.springframework.aot.generate.GenerationContext generationContext =
        mock(org.springframework.aot.generate.GenerationContext.class);
    RuntimeHints hints = new RuntimeHints();
    when(generationContext.getRuntimeHints()).thenReturn(hints);

    org.springframework.beans.factory.aot.BeanRegistrationCode beanRegistrationCode =
        mock(org.springframework.beans.factory.aot.BeanRegistrationCode.class);

    contribution.applyTo(generationContext, beanRegistrationCode);

    assertThat(RuntimeHintsPredicates.reflection().onType(TestEventHandler.class).test(hints))
        .isTrue();
  }

  @Test
  void contributionRegistersRuntimeHintsForQueryHandler() throws Exception {
    RegisteredBean registeredBean = mock(RegisteredBean.class);
    when(registeredBean.getBeanClass()).thenReturn((Class) TestQueryHandler.class);

    BeanRegistrationAotContribution contribution = processor.processAheadOfTime(registeredBean);
    assertThat(contribution).isNotNull();

    org.springframework.aot.generate.GenerationContext generationContext =
        mock(org.springframework.aot.generate.GenerationContext.class);
    RuntimeHints hints = new RuntimeHints();
    when(generationContext.getRuntimeHints()).thenReturn(hints);

    org.springframework.beans.factory.aot.BeanRegistrationCode beanRegistrationCode =
        mock(org.springframework.beans.factory.aot.BeanRegistrationCode.class);

    contribution.applyTo(generationContext, beanRegistrationCode);

    assertThat(RuntimeHintsPredicates.reflection().onType(TestQueryHandler.class).test(hints))
        .isTrue();
  }
}
