package com.borjaglez.cqrs.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.ValidatedCommand;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

class CommandValidationInterceptorTest {

  private Validator validator;
  private CommandValidationInterceptor interceptor;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    interceptor = new CommandValidationInterceptor(validator);
  }

  @Test
  void validCommandPassesThrough() throws Exception {
    MiddlewareChain chain = mock(MiddlewareChain.class);
    when(chain.proceed(any())).thenReturn("ok");

    ValidatedCommand command = new ValidatedCommand("valid-name");
    Object result = interceptor.process(command, chain);

    assertThat(result).isEqualTo("ok");
    verify(chain).proceed(command);
  }

  @Test
  void invalidCommandThrowsConstraintViolationException() {
    MiddlewareChain chain = mock(MiddlewareChain.class);

    ValidatedCommand command = new ValidatedCommand("");

    assertThatThrownBy(() -> interceptor.process(command, chain))
        .isInstanceOf(ConstraintViolationException.class);

    verifyNoInteractions(chain);
  }

  @Test
  void nonCommandMessagePassesThrough() throws Exception {
    MiddlewareChain chain = mock(MiddlewareChain.class);
    when(chain.proceed(any())).thenReturn("passed");

    Object nonCommand = "not-a-command";
    Object result = interceptor.process(nonCommand, chain);

    assertThat(result).isEqualTo("passed");
    verify(chain).proceed(nonCommand);
  }
}
