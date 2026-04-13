package com.borjaglez.cqrs.example.middleware.middleware;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.borjaglez.cqrs.context.MessageContext;

/**
 * Seeds a {@link MessageContext} from inbound HTTP headers so every CQRS dispatch triggered by the
 * request inherits the correlation ID and tenant ID. Returning from the filter automatically
 * restores the previous context via {@link MessageContext.Scope}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MessageContextFilter implements Filter {

  private static final String CORRELATION_HEADER = "X-Correlation-Id";
  private static final String TENANT_HEADER = "X-Tenant-Id";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest http = (HttpServletRequest) request;

    MessageContext ctx = MessageContext.empty();
    String correlationId = http.getHeader(CORRELATION_HEADER);
    if (correlationId != null) {
      ctx = ctx.with(MessageContext.CORRELATION_ID_KEY, correlationId);
    }
    String tenantId = http.getHeader(TENANT_HEADER);
    if (tenantId != null) {
      ctx = ctx.with("tenantId", tenantId);
    }

    MessageContext.Scope scope = MessageContext.scope(ctx);
    try {
      chain.doFilter(request, response);
    } finally {
      scope.close();
    }
  }
}
