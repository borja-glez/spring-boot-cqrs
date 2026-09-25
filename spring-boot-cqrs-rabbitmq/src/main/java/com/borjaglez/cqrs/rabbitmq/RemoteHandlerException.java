package com.borjaglez.cqrs.rabbitmq;

/**
 * The handler of a command or query sent over RabbitMQ failed. Carries the class name of the
 * exception it threw, since the exception itself cannot cross the wire.
 */
public class RemoteHandlerException extends RuntimeException {

  private final String remoteExceptionType;

  public RemoteHandlerException(String remoteExceptionType, String message) {
    super("Remote handler error: " + message);
    this.remoteExceptionType = remoteExceptionType;
  }

  /** Fully qualified class name of the remote exception, or {@code null} if unknown. */
  public String getRemoteExceptionType() {
    return remoteExceptionType;
  }
}
