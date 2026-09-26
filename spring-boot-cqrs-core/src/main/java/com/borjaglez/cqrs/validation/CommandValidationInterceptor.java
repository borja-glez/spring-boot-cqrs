package com.borjaglez.cqrs.validation;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

/**
 * Validates commands with Bean Validation and throws {@link ConstraintViolationException} when a
 * command is invalid, before its handler runs.
 *
 * <p>Runs in every {@link DispatchPhase}. On the sending side of a remote bus an invalid command
 * fails with {@link ConstraintViolationException} before anything is published; the receiver
 * validates again, because it cannot trust every producer.
 */
public class CommandValidationInterceptor implements BusMiddleware {

  private final Validator validator;

  public CommandValidationInterceptor(Validator validator) {
    this.validator = validator;
  }

  /** Every phase: local dispatches, the sender and the receiver of remote messages. */
  @Override
  public Set<DispatchPhase> phases() {
    return Set.of(DispatchPhase.values());
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    if (message instanceof Command) {
      Set<ConstraintViolation<Object>> violations = validator.validate(message);
      if (!violations.isEmpty()) {
        throw new ConstraintViolationException(violations);
      }
    }
    return chain.proceed(message);
  }
}
