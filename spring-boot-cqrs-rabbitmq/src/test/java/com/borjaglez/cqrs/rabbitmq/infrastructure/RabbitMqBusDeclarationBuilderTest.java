package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitMqBusDeclarationBuilderTest {

  private RabbitMqBusDeclarationBuilder builder;
  private DefaultRabbitMqNamingStrategy namingStrategy;

  @BeforeEach
  void setUp() {
    namingStrategy = new DefaultRabbitMqNamingStrategy("cqrs");
    builder = new RabbitMqBusDeclarationBuilder(namingStrategy);
  }

  @Test
  void buildWithRetryAndDeadLetterShouldCreateCorrectDeclarables() {
    List<String> routingKeys = List.of("test.order.create", "test.order.update");

    Declarables declarables =
        builder.buildWithRetryAndDeadLetter("my-app", "commands", routingKeys, 5000);

    List<Declarable> all = declarables.getDeclarables().stream().toList();

    // 3 exchanges (main, retry, dead_letter) + 3 queues + 4 bindings (2 routing keys + retry + DL)
    assertThat(all).hasSize(10);

    List<TopicExchange> exchanges =
        all.stream().filter(d -> d instanceof TopicExchange).map(d -> (TopicExchange) d).toList();
    assertThat(exchanges).hasSize(3);
    assertThat(exchanges.stream().map(TopicExchange::getName))
        .containsExactlyInAnyOrder(
            "cqrs.commands", "cqrs.commands.retry", "cqrs.commands.dead_letter");

    List<Queue> queues = all.stream().filter(d -> d instanceof Queue).map(d -> (Queue) d).toList();
    assertThat(queues).hasSize(3);
    assertThat(queues.stream().map(Queue::getName))
        .containsExactlyInAnyOrder(
            "cqrs.my-app.commands",
            "cqrs.my-app.commands.retry",
            "cqrs.my-app.commands.dead_letter");

    List<Binding> bindings =
        all.stream().filter(d -> d instanceof Binding).map(d -> (Binding) d).toList();
    assertThat(bindings).hasSize(4);
  }

  @Test
  void buildWithRetryAndDeadLetterShouldConfigureRetryQueueWithTtlAndDlx() {
    Declarables declarables =
        builder.buildWithRetryAndDeadLetter(
            "my-app", "commands", List.of("test.order.create"), 3000);

    List<Declarable> all = declarables.getDeclarables().stream().toList();
    Queue retryQueue =
        all.stream()
            .filter(d -> d instanceof Queue)
            .map(d -> (Queue) d)
            .filter(q -> q.getName().contains("retry"))
            .findFirst()
            .orElseThrow();

    assertThat(retryQueue.getArguments()).containsEntry("x-message-ttl", 3000);
  }

  @Test
  void retryQueueShouldDeadLetterThroughTheDefaultExchangeToTheAppMainQueue() {
    Declarables declarables =
        builder.buildWithRetryAndDeadLetter(
            "my-app", "commands", List.of("test.order.create"), 3000);

    Queue retryQueue = queueNamed(declarables, "cqrs.my-app.commands.retry");

    assertThat(retryQueue.getArguments())
        .containsEntry("x-dead-letter-exchange", "")
        .containsEntry("x-dead-letter-routing-key", "cqrs.my-app.commands");
  }

  @Test
  void retryAndDeadLetterQueuesShouldBeBoundWithTheAppNameOnly() {
    Declarables declarables =
        builder.buildWithRetryAndDeadLetter(
            "my-app", "commands", List.of("test.order.create"), 3000);

    Binding retryBinding = bindingOf(declarables, "cqrs.my-app.commands.retry");
    assertThat(retryBinding.getExchange()).isEqualTo("cqrs.commands.retry");
    assertThat(retryBinding.getRoutingKey()).isEqualTo("my-app");

    Binding deadLetterBinding = bindingOf(declarables, "cqrs.my-app.commands.dead_letter");
    assertThat(deadLetterBinding.getExchange()).isEqualTo("cqrs.commands.dead_letter");
    assertThat(deadLetterBinding.getRoutingKey()).isEqualTo("my-app");
  }

  @Test
  void twoAppsShouldNotShareRetryOrDeadLetterRoutingKeys() {
    List<String> routingKeys = List.of("test.order.create");
    Declarables app1 = builder.buildWithRetryAndDeadLetter("app-1", "events", routingKeys, 1000);
    Declarables app2 = builder.buildWithRetryAndDeadLetter("app-2", "events", routingKeys, 1000);

    assertThat(bindingOf(app1, "cqrs.app-1.events.retry").getRoutingKey())
        .isNotEqualTo(bindingOf(app2, "cqrs.app-2.events.retry").getRoutingKey());
    assertThat(bindingOf(app1, "cqrs.app-1.events.dead_letter").getRoutingKey())
        .isNotEqualTo(bindingOf(app2, "cqrs.app-2.events.dead_letter").getRoutingKey());
  }

  private static Queue queueNamed(Declarables declarables, String name) {
    return declarables.getDeclarablesByType(Queue.class).stream()
        .filter(q -> q.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static Binding bindingOf(Declarables declarables, String queueName) {
    return declarables.getDeclarablesByType(Binding.class).stream()
        .filter(b -> b.getDestination().equals(queueName))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void buildWithRetryAndDeadLetterWithEmptyRoutingKeys() {
    Declarables declarables =
        builder.buildWithRetryAndDeadLetter("my-app", "commands", List.of(), 1000);

    List<Declarable> all = declarables.getDeclarables().stream().toList();

    // 3 exchanges + 3 queues + 2 bindings (retry + DL only, no main routing keys)
    assertThat(all).hasSize(8);
  }

  @Test
  void buildSimpleShouldCreateExchangeQueueAndBindings() {
    List<String> routingKeys = List.of("test.order.get", "test.order.list");

    Declarables declarables = builder.buildSimple("my-app", "queries", routingKeys);

    List<Declarable> all = declarables.getDeclarables().stream().toList();

    // 1 exchange + 1 queue + 2 bindings
    assertThat(all).hasSize(4);

    List<TopicExchange> exchanges =
        all.stream().filter(d -> d instanceof TopicExchange).map(d -> (TopicExchange) d).toList();
    assertThat(exchanges).hasSize(1);
    assertThat(exchanges.get(0).getName()).isEqualTo("cqrs.queries");

    List<Queue> queues = all.stream().filter(d -> d instanceof Queue).map(d -> (Queue) d).toList();
    assertThat(queues).hasSize(1);
    assertThat(queues.get(0).getName()).isEqualTo("cqrs.my-app.queries");

    List<Binding> bindings =
        all.stream().filter(d -> d instanceof Binding).map(d -> (Binding) d).toList();
    assertThat(bindings).hasSize(2);
  }

  @Test
  void buildSimpleWithEmptyRoutingKeys() {
    Declarables declarables = builder.buildSimple("my-app", "queries", List.of());

    List<Declarable> all = declarables.getDeclarables().stream().toList();

    // 1 exchange + 1 queue + 0 bindings
    assertThat(all).hasSize(2);
  }
}
