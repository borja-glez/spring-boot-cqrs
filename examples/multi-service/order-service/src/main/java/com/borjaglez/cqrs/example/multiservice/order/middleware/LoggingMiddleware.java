package com.borjaglez.cqrs.example.multiservice.order.middleware;

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
    String messageType = message.getClass().getSimpleName();
    log.info("Processing: {}", messageType);
    long start = System.currentTimeMillis();
    try {
      Object result = chain.proceed(message);
      log.info("Completed {} in {}ms", messageType, System.currentTimeMillis() - start);
      return result;
    } catch (Exception e) {
      log.error("Failed {}: {}", messageType, e.getMessage());
      throw e;
    }
  }
}
