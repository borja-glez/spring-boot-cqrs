package com.borjaglez.cqrs.rabbitmq;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

/**
 * {@link CommandBus} that sends commands to the RabbitMQ commands exchange.
 *
 * <p>Before a command is published, it passes the middlewares that declare {@link
 * DispatchPhase#OUTBOUND}, in the sending process. A middleware can stop the send by throwing: an
 * invalid command then fails with the exception of the validation middleware and nothing is
 * published. The receiving service runs its {@link DispatchPhase#INBOUND} middlewares again.
 */
public class RabbitMqCommandBus implements CommandBus {

  private final RabbitMqPublisher publisher;
  private final RabbitMqNamingStrategy rabbitNaming;
  private final MessageNamingStrategy messageNaming;
  private final String exchangeName;
  private final List<BusMiddleware> middlewares;

  /** Creates a bus that runs no middleware before sending. */
  public RabbitMqCommandBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName) {
    this(publisher, rabbitNaming, messageNaming, exchangeName, List.of());
  }

  /**
   * Creates a bus that runs, before sending, the middlewares of {@code middlewares} that declare
   * {@link DispatchPhase#OUTBOUND}, in the order of the list.
   */
  public RabbitMqCommandBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName,
      List<BusMiddleware> middlewares) {
    this.publisher = publisher;
    this.rabbitNaming = rabbitNaming;
    this.messageNaming = messageNaming;
    this.exchangeName = exchangeName;
    this.middlewares = DispatchPhase.OUTBOUND.select(middlewares);
  }

  @Override
  public void dispatch(Command command) {
    send(
        command,
        (exchange, routingKey, message) -> {
          publisher.publish(exchange, routingKey, message, "command");
          return null;
        });
  }

  @Override
  public void dispatchAndWait(Command command) {
    send(
        command,
        (exchange, routingKey, message) ->
            publisher.publishAndReceive(exchange, routingKey, message, "command_wait"));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatchAndReceive(Command command) {
    return (R)
        send(
            command,
            (exchange, routingKey, message) ->
                publisher.publishAndReceive(exchange, routingKey, message, "command_reply"));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatchAndReceive(Command command, ParameterizedTypeReference<R> responseType) {
    return (R)
        send(
            command,
            (exchange, routingKey, message) ->
                publisher.publishAndReceive(
                    exchange, routingKey, message, "command_reply", responseType));
  }

  /** Runs the outbound middlewares with {@code sender} as the end of the chain. */
  private Object send(Command command, Sender sender) {
    try {
      return new DefaultMiddlewareChain(
              middlewares,
              message ->
                  sender.send(
                      rabbitNaming.exchange(exchangeName),
                      messageNaming.commandName(message.getClass()),
                      message))
          .proceed(command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  @FunctionalInterface
  private interface Sender {
    Object send(String exchange, String routingKey, Object message);
  }
}
