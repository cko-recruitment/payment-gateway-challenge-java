package com.checkout.payment.gateway.configuration;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Adds security headers to all HTTP responses.
 * Mitigates common web vulnerabilities identified by DAST scanners.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletResponse httpResponse = (HttpServletResponse) response;

    // Prevent clickjacking attacks
    httpResponse.setHeader("X-Frame-Options", "DENY");

    // Prevent MIME type sniffing
    httpResponse.setHeader("X-Content-Type-Options", "nosniff");

    // Enable XSS protection in browsers
    httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

    // Content Security Policy - restrict resource loading
    httpResponse.setHeader("Content-Security-Policy",
        "default-src 'self'; frame-ancestors 'none';");

    // Strict Transport Security - enforce HTTPS
    httpResponse.setHeader("Strict-Transport-Security",
        "max-age=31536000; includeSubDomains");

    // Referrer Policy
    httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

    // Permissions Policy
    httpResponse.setHeader("Permissions-Policy",
        "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()");

    chain.doFilter(request, response);
  }
}
