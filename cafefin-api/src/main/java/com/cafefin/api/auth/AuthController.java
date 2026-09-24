package com.cafefin.api.auth;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final LoginRateLimiter loginRateLimiter;
  private final RegisterRateLimiter registerRateLimiter;

  public AuthController(
      AuthService authService,
      LoginRateLimiter loginRateLimiter,
      RegisterRateLimiter registerRateLimiter) {
    this.authService = authService;
    this.loginRateLimiter = loginRateLimiter;
    this.registerRateLimiter = registerRateLimiter;
  }

  /**
   * Task 1.8.4.2: one Bucket4j token per client IP is spent before the email/password are even
   * validated — same "throttle the attempt itself" reasoning as {@link #login}.
   */
  @PostMapping("/register")
  public ResponseEntity<Object> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    ConsumptionProbe probe = registerRateLimiter.tryConsume(servletRequest.getRemoteAddr());
    if (!probe.isConsumed()) {
      return tooManyRequests(
          servletRequest, probe, "too many registration attempts, try again later");
    }
    User user = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(user));
  }

  /**
   * Task 1.8.4.1: one Bucket4j token per {@code (email, client IP)} pair is spent before
   * credentials are even checked — a client that has exhausted its bucket gets {@code 429}
   * whether the password it sent was right or wrong, since the point is to slow down the
   * attempt itself, not just failed ones.
   */
  @PostMapping("/login")
  public ResponseEntity<Object> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    ConsumptionProbe probe =
        loginRateLimiter.tryConsume(request.email(), servletRequest.getRemoteAddr());
    if (!probe.isConsumed()) {
      return tooManyRequests(servletRequest, probe, "too many login attempts, try again later");
    }
    return ResponseEntity.ok(authService.login(request));
  }

  private ResponseEntity<Object> tooManyRequests(
      HttpServletRequest servletRequest, ConsumptionProbe probe, String detail) {
    // Round the wait up to a whole second: Retry-After is defined in whole
    // seconds (RFC 9110 §10.2.3), and rounding down could tell the client
    // to retry a moment before its token has actually refilled.
    long retryAfterSeconds =
        Math.ceilDiv(probe.getNanosToWaitForRefill(), Duration.ofSeconds(1).toNanos());

    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
    problem.setDetail(detail);
    problem.setInstance(URI.create(servletRequest.getRequestURI()));

    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(authService.refresh(request));
  }
}
