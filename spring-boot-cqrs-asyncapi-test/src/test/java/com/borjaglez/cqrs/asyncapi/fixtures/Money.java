package com.borjaglez.cqrs.asyncapi.fixtures;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

  public static Money euros(String amount) {
    return new Money(new BigDecimal(amount), "EUR");
  }
}
