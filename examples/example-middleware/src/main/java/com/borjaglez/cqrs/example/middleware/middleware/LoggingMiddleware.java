package com.borjaglez.cqrs.example.middleware.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

@Component
@Order(1)
public class LoggingMiddleware implements BusMiddleware {

  private static final Logger log = LoggerFactory.getLogger(LoggingMiddleware.class);

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    String messageName = message.getClass().getSimpleName();
    log.info("Processing message: {}", messageName);

    long start = System.currentTimeMillis();
    try {
      Object result = chain.proceed(message);
      long duration = System.currentTimeMillis() - start;
      log.info("Processed message: {} in {}ms", messageName, duration);
      return result;
    } catch (Exception e) {
      log.error("Failed to process message: {}", messageName, e);
      throw e;
    }
  }
}
