package com.checkout.payment.gateway.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

  private final RequestLoggingFilter filter = new RequestLoggingFilter();

  @Test
  void correlatesRequestAndLogsOnlySafeMetadata(CapturedOutput output) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secret-path");
    request.addHeader("X-Request-ID", "untrusted-header");
    request.setQueryString("cvv=secret-query");
    request.setContent("secret-body".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {
      String id = response.getHeader("X-Request-ID");
      assertNotNull(UUID.fromString(id));
      assertEquals(id, MDC.get("requestId"));
      req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/payments");
      response.setStatus(400);
    });

    assertNull(MDC.get("requestId"));
    assertTrue(output.getOut().contains("route=/payments status=400 duration_ms="));
    for (String secret : new String[]{"secret-path", "secret-query", "secret-body",
        "untrusted-header"}) {
      assertFalse(output.getOut().contains(secret));
    }
  }

  @Test
  void restoresContextAndReportsUnhandledFailure(CapturedOutput output) {
    MDC.put("requestId", "previous-context");
    try {
      assertThrows(ServletException.class, () -> filter.doFilter(
          new MockHttpServletRequest("GET", "/private-path"),
          new MockHttpServletResponse(),
          (req, res) -> {
            throw new ServletException("secret exception");
          }));

      assertEquals("previous-context", MDC.get("requestId"));
      assertTrue(output.getOut().contains("route=unmatched status=500 duration_ms="));
      assertFalse(output.getOut().contains("secret exception"));
    } finally {
      MDC.remove("requestId");
    }
  }
}
