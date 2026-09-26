package com.borjaglez.cqrs.rabbitmq;

/**
 * No reply arrived in time for a command or query sent over RabbitMQ. The handler may still run, or
 * may have run already: callers that retry must send commands whose handlers are idempotent. The
 * wait is {@code spring.rabbitmq.template.reply-timeout} (5 seconds by default).
 */
public class RemoteReplyTimeoutException extends RuntimeException {

  public RemoteReplyTimeoutException(String exchange, String routingKey) {
    super("No reply to " + routingKey + " sent to " + exchange + " before the reply timeout");
  }
}
