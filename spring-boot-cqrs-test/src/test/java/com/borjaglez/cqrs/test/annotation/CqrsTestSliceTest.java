package com.borjaglez.cqrs.test.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.discovery.BeanPostProcessorHandlerDiscoverer;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.test.bus.SpyCommandBus;
import com.borjaglez.cqrs.test.bus.SpyEventBus;
import com.borjaglez.cqrs.test.bus.SpyQueryBus;
import com.borjaglez.cqrs.test.fixtures.SampleCommandHandlerBean;
import com.borjaglez.cqrs.test.fixtures.SampleEventHandlerBean;
import com.borjaglez.cqrs.test.fixtures.SampleQueryHandlerBean;
import com.borjaglez.cqrs.test.fixtures.TestCommand;
import com.borjaglez.cqrs.test.fixtures.TestEvent;
import com.borjaglez.cqrs.test.fixtures.TestQuery;

@CqrsTest
@Import({
  SampleCommandHandlerBean.class,
  SampleEventHandlerBean.class,
  SampleQueryHandlerBean.class
})
class CqrsTestSliceTest {

  @Autowired SpyCommandBus commandBus;
  @Autowired SpyEventBus eventBus;
  @Autowired SpyQueryBus queryBus;
  @Autowired SampleEventHandlerBean eventHandler;
  @Autowired CommandHandlerRegistry commandRegistry;
  @Autowired EventHandlerRegistry eventRegistry;
  @Autowired QueryHandlerRegistry queryRegistry;
  @Autowired BeanPostProcessorHandlerDiscoverer discoverer;
  @Autowired MessageNamingStrategy namingStrategy;

  @Test
  void infrastructureBeansAreRegistered() {
    assertThat(commandRegistry).isNotNull();
    assertThat(eventRegistry).isNotNull();
    assertThat(queryRegistry).isNotNull();
    assertThat(discoverer).isNotNull();
    assertThat(namingStrategy).isNotNull();
  }

  @Test
  void commandIsHandledAndSpied() {
    String result = commandBus.dispatchAndReceive(new TestCommand("a"));

    assertThat(result).isEqualTo("handled:a");
    assertThat(commandBus).dispatched(TestCommand.class).once();
  }

  @Test
  void eventIsHandledAndSpied() {
    eventBus.publish(new TestEvent("payload"));

    assertThat(eventHandler.getReceived()).containsExactly("payload");
    assertThat(eventBus).published(TestEvent.class).once();
  }

  @Test
  void queryIsHandledAndSpied() {
    String result = queryBus.ask(new TestQuery("x"));

    assertThat(result).isEqualTo("answer:x");
    assertThat(queryBus).asked(TestQuery.class).once();
  }
}
