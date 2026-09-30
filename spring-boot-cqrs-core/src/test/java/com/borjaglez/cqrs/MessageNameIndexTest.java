package com.borjaglez.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestQuery;

class MessageNameIndexTest {

  private final MessageNameIndex index = new MessageNameIndex();

  @Test
  void findsTheClassRegisteredUnderAName() {
    index.add("svc.1.command.mod.create", TestCommand.class);

    assertThat(index.find("svc.1.command.mod.create")).contains(TestCommand.class);
  }

  @Test
  void unknownNameResolvesToNothing() {
    assertThat(index.find("svc.1.command.mod.other")).isEmpty();
  }

  @Test
  void nullNameIsIgnoredAndResolvesToNothing() {
    index.add(null, TestCommand.class);

    assertThat(index.find(null)).isEmpty();
  }

  @Test
  void registeringTheSameClassTwiceKeepsItResolvable() {
    index.add("svc.1.event.mod.created", TestEvent.class);
    index.add("svc.1.event.mod.created", TestEvent.class);

    assertThat(index.find("svc.1.event.mod.created")).contains(TestEvent.class);
  }

  @Test
  void ambiguousNameResolvesToNothing() {
    index.add("shared", TestCommand.class);
    index.add("shared", TestEvent.class);
    // A third class under a name already known to be ambiguous is not reported again.
    index.add("shared", TestQuery.class);

    assertThat(index.find("shared")).isEmpty();
  }
}
