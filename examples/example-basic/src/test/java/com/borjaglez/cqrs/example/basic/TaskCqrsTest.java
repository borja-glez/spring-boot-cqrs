package com.borjaglez.cqrs.example.basic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.example.basic.command.CreateTaskCommand;
import com.borjaglez.cqrs.example.basic.command.CreateTaskCommandHandler;
import com.borjaglez.cqrs.example.basic.domain.InMemoryTaskRepository;
import com.borjaglez.cqrs.example.basic.domain.Task;
import com.borjaglez.cqrs.example.basic.event.TaskCreatedEvent;
import com.borjaglez.cqrs.example.basic.event.TaskEventHandler;
import com.borjaglez.cqrs.example.basic.query.GetTaskQuery;
import com.borjaglez.cqrs.example.basic.query.TaskQueryHandler;
import com.borjaglez.cqrs.test.annotation.CqrsTest;
import com.borjaglez.cqrs.test.bus.InMemoryQueryBus;
import com.borjaglez.cqrs.test.bus.SpyCommandBus;
import com.borjaglez.cqrs.test.bus.SpyEventBus;

/**
 * Demonstrates spring-boot-cqrs-test in action: the {@link CqrsTest} slice boots only the CQRS
 * infrastructure with spy-wrapped buses, while {@link InMemoryQueryBus} is used for a standalone
 * unit test that does not require Spring.
 */
@CqrsTest
@Import({
  CreateTaskCommandHandler.class,
  TaskQueryHandler.class,
  TaskEventHandler.class,
  InMemoryTaskRepository.class
})
class TaskCqrsTest {

  @Autowired SpyCommandBus commandBus;
  @Autowired SpyEventBus eventBus;

  @Test
  void creatingTaskPublishesTaskCreatedEvent() {
    String taskId = commandBus.dispatchAndReceive(new CreateTaskCommand("buy milk", "groceries"));

    assertThat(taskId).isNotBlank();
    assertThat(commandBus).dispatched(CreateTaskCommand.class).once();
    assertThat(eventBus)
        .published(TaskCreatedEvent.class)
        .once()
        .matching(e -> ((TaskCreatedEvent) e).getTitle().equals("buy milk"));
  }

  @Test
  void inMemoryQueryBusAnswersStandaloneWithoutSpring() {
    Task task = new Task("id-1", "title", "desc");
    InMemoryQueryBus standaloneBus =
        new InMemoryQueryBus().register(GetTaskQuery.class, q -> task);

    Task result = standaloneBus.ask(new GetTaskQuery("id-1"));

    assertThat(result).isSameAs(task);
  }
}
