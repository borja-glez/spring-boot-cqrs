package com.borjaglez.cqrs.rabbitmq;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

public class RabbitMqCommandBus implements CommandBus {

  private final RabbitMqPublisher publisher;
  private final RabbitMqNamingStrategy rabbitNaming;
  private final MessageNamingStrategy messageNaming;
  private final String exchangeName;

  public RabbitMqCommandBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName) {
    this.publisher = publisher;
    this.rabbitNaming = rabbitNaming;
    this.messageNaming = messageNaming;
    this.exchangeName = exchangeName;
  }

  @Override
  public void dispatch(Command command) {
    String exchange = rabbitNaming.exchange(exchangeName);
    String routingKey = messageNaming.commandName(command.getClass());
    publisher.publish(exchange, routingKey, command, "command");
  }

  @Override
  public void dispatchAndWait(Command command) {
    String exchange = rabbitNaming.exchange(exchangeName);
    String routingKey = messageNaming.commandName(command.getClass());
    try {
      publisher.publishAndReceive(exchange, routingKey, command, "command_wait");
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatchAndReceive(Command command) {
    String exchange = rabbitNaming.exchange(exchangeName);
    String routingKey = messageNaming.commandName(command.getClass());
    try {
      return (R) publisher.publishAndReceive(exchange, routingKey, command, "command_reply");
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }
}
