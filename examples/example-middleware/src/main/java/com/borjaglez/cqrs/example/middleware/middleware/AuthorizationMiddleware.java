package com.borjaglez.cqrs.example.middleware.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

@Component
@Order(2)
public class AuthorizationMiddleware implements BusMiddleware {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationMiddleware.class);

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    String messageName = message.getClass().getSimpleName();
    log.info("Authorizing message: {}", messageName);
    return chain.proceed(message);
  }
}
