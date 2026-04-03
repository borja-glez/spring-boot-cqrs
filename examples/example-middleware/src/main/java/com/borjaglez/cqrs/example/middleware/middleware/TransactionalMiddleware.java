package com.borjaglez.cqrs.example.middleware.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

@Component
@Order(3)
public class TransactionalMiddleware implements BusMiddleware {

  private static final Logger log = LoggerFactory.getLogger(TransactionalMiddleware.class);

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    String messageName = message.getClass().getSimpleName();
    log.info("Starting transaction for: {}", messageName);

    try {
      Object result = chain.proceed(message);
      log.info("Committing transaction for: {}", messageName);
      return result;
    } catch (Exception e) {
      log.error("Rolling back transaction for: {}", messageName, e);
      throw e;
    }
  }
}
