package com.borjaglez.cqrs.kafka.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.KafkaCommandBus;
import com.borjaglez.cqrs.kafka.config.KafkaCommandBusAutoConfiguration;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsAutoConfiguration;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Broker-backed check that a command sent with a reply carries the caller's {@link MessageContext}
 * to its handler, as fire-and-forget messages do.
 */
@EmbeddedKafka(partitions = 1)
class KafkaRequestReplyContextIT {

  @Test
  void handlerOfDispatchAndReceiveSeesTheCallersContext(EmbeddedKafkaBroker broker) {
    AtomicReference<Map<String, String>> seen = new AtomicReference<>();
    new ApplicationContextRunner()
        .withPropertyValues(
            "spring.application.name=context-it",
            "spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
            "cqrs.kafka.prefix=context-it",
            "cqrs.kafka.events.enabled=false",
            "cqrs.kafka.queries.enabled=false",
            "cqrs.kafka.replies.timeout=30s",
            "cqrs.context.header-prefix=x-ctx-")
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(ContextCapturingHandler.class, () -> new ContextCapturingHandler(seen))
        .withConfiguration(
            AutoConfigurations.of(
                CqrsAutoConfiguration.class,
                CqrsSerializationAutoConfiguration.class,
                KafkaAutoConfiguration.class,
                KafkaCqrsAutoConfiguration.class,
                KafkaCommandBusAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              KafkaCommandBus commandBus = context.getBean(KafkaCommandBus.class);
              MessageContext callerContext =
                  MessageContext.empty()
                      .with(MessageContext.CORRELATION_ID_KEY, "cid-81")
                      .with("tenantId", "acme");

              String result;
              try (MessageContext.Scope ignored = MessageContext.scope(callerContext)) {
                result = commandBus.dispatchAndReceive(new ContextCommand("ping"));
              }

              assertThat(result).isEqualTo("pong");
              assertThat(seen.get())
                  .containsEntry(MessageContext.CORRELATION_ID_KEY, "cid-81")
                  .containsEntry("tenantId", "acme");
            });
  }

  @Getter
  @NoArgsConstructor
  @CqrsMessage(service = "context-it", module = "context", name = "ping")
  public static class ContextCommand extends Command {

    private String value;

    public ContextCommand(String value) {
      this.value = value;
    }
  }

  @CommandHandler
  public static class ContextCapturingHandler {

    private final AtomicReference<Map<String, String>> seen;

    ContextCapturingHandler(AtomicReference<Map<String, String>> seen) {
      this.seen = seen;
    }

    @HandleCommand
    public String handle(ContextCommand command) {
      seen.set(MessageContext.current().asMap());
      return "pong";
    }
  }
}
