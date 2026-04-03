package com.borjaglez.cqrs.naming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.fixtures.UnannotatedCommand;

class DefaultMessageNamingStrategyTest {

  @Test
  void commandNameWithAnnotationAndPrefix() {
    DefaultMessageNamingStrategy strategy = new DefaultMessageNamingStrategy("amj");
    String name = strategy.commandName(TestCommand.class);
    assertThat(name).isEqualTo("amj.test.1.command.order.create_order");
  }

  @Test
  void eventNameWithAnnotationAndPrefix() {
    DefaultMessageNamingStrategy strategy = new DefaultMessageNamingStrategy("amj");
    String name = strategy.eventName(TestEvent.class);
    assertThat(name).isEqualTo("amj.test.1.event.order.order_created");
  }

  @Test
  void queryNameWithAnnotationAndPrefix() {
    DefaultMessageNamingStrategy strategy = new DefaultMessageNamingStrategy("amj");
    String name = strategy.queryName(TestQuery.class);
    assertThat(name).isEqualTo("amj.test.1.query.order.get_order");
  }

  @Test
  void commandNameWithEmptyPrefix() {
    DefaultMessageNamingStrategy strategy = new DefaultMessageNamingStrategy("");
    String name = strategy.commandName(TestCommand.class);
    assertThat(name).isEqualTo("test.1.command.order.create_order");
  }

  @Test
  void commandNameWithNullPrefixTreatedAsEmpty() {
    DefaultMessageNamingStrategy strategy = new DefaultMessageNamingStrategy(null);
    String name = strategy.commandName(TestCommand.class);
    assertThat(name).isEqualTo("test.1.command.order.create_order");
  }

  @Test
  void fallbackToKebabCaseForUnannotatedCommand() {
    DefaultMessageNamingStrategy strategy = new DefaultMessageNamingStrategy("amj");
    String name = strategy.commandName(UnannotatedCommand.class);
    assertThat(name).isEqualTo("unannotated-command");
  }
}
