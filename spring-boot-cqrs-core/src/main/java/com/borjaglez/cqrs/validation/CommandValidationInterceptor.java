package com.borjaglez.cqrs.validation;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

public class CommandValidationInterceptor implements BusMiddleware {

  private final Validator validator;

  public CommandValidationInterceptor(Validator validator) {
    this.validator = validator;
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
