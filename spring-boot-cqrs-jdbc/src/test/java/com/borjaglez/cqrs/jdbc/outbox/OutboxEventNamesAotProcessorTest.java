package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import com.borjaglez.cqrs.fixtures.outbox.TestRenamedEvent;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;

class OutboxEventNamesAotProcessorTest {

  private final MessageNamingStrategy naming = new DefaultMessageNamingStrategy("");

  private DefaultListableBeanFactory beanFactory(MockEnvironment environment) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("environment", environment);
    beanFactory.registerSingleton("messageNamingStrategy", naming);
    return beanFactory;
  }

  @Test
  void writesTheNameIndexAndKeepsItInTheImage() throws Exception {
    BeanFactoryInitializationAotContribution contribution =
        new OutboxEventNamesAotProcessor()
            .processAheadOfTime(
                beanFactory(
                    new MockEnvironment()
                        .withProperty("cqrs.outbox.enabled", "true")
                        .withProperty(
                            "cqrs.aot.message-packages", "com.borjaglez.cqrs.fixtures.outbox")));
    GenerationContext generationContext = mock(GenerationContext.class);
    GeneratedFiles files = mock(GeneratedFiles.class);
    RuntimeHints hints = new RuntimeHints();
    when(generationContext.getGeneratedFiles()).thenReturn(files);
    when(generationContext.getRuntimeHints()).thenReturn(hints);

    contribution.applyTo(generationContext, mock(BeanFactoryInitializationCode.class));

    ArgumentCaptor<CharSequence> content = ArgumentCaptor.forClass(CharSequence.class);
    verify(files).addResourceFile(eq(OutboxEventTypeResolver.INDEX_LOCATION), content.capture());
    Properties index = new Properties();
    index.load(new StringReader(content.getValue().toString()));
    assertThat(index)
        .containsEntry(naming.eventName(TestRenamedEvent.class), TestRenamedEvent.class.getName());
    assertThat(
            RuntimeHintsPredicates.resource().forResource(OutboxEventTypeResolver.INDEX_LOCATION))
        .accepts(hints);
  }

  @Test
  void contributesNothingWhenTheOutboxIsDisabled() {
    assertThat(
            new OutboxEventNamesAotProcessor()
                .processAheadOfTime(beanFactory(new MockEnvironment())))
        .isNull();
  }
}
