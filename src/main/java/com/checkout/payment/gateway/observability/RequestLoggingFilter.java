package com.checkout.payment.gateway.observability;

import static com.checkout.payment.gateway.observability.ObservabilityConstants.REQUEST_ID_HEADER;
import static com.checkout.payment.gateway.observability.ObservabilityConstants.REQUEST_ID_MDC_KEY;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String previousId = MDC.get(REQUEST_ID_MDC_KEY);
    String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    long started = System.nanoTime();
    boolean completed = false;
    try {
      filterChain.doFilter(request, response);
      completed = true;
    } finally {
      try {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        // Use the matched route template, never raw URLs, queries, headers or bodies.
        LOG.info("http_request request_id={} method={} route={} status={} duration_ms={}",
            requestId, request.getMethod(), route == null ? "unmatched" : route,
            completed ? response.getStatus() : 500,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
      } finally {
        if (previousId == null) {
          MDC.remove(REQUEST_ID_MDC_KEY);
        } else {
          MDC.put(REQUEST_ID_MDC_KEY, previousId);
        }
      }
    }
  }
}
