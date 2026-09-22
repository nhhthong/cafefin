package com.cafefin.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Task 1.1.8 follow-up: Spring Security's built-in {@code HttpStatusEntryPoint} sends a bare
 * {@code 401} with no body. Every other error in this app (validation failures, the register
 * conflict, login's own {@code 401}, ...) is an RFC 9457 {@code ProblemDetail} — but
 * {@code spring.mvc.problemdetails.enabled} only covers exceptions Spring MVC's dispatcher sees,
 * and the security filter chain rejects an unauthenticated request *before* it ever reaches MVC.
 * This entry point builds the same body by hand so the convention holds everywhere, not just
 * inside controller methods.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problem.setDetail("authentication required");
    problem.setInstance(URI.create(request.getRequestURI()));

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
