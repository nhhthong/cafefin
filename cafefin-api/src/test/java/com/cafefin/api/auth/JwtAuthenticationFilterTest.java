package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Task 1.1.9/1.1.10: unit-level (no Spring context, no HTTP endpoint needed) — a real
 * {@link JwtAuthenticationFilter} instance, fed a request with a valid RS256 token, lets the
 * request through and authenticates it as the token's subject. A token expired by less than the
 * 60-second clock-skew tolerance still authenticates; one expired by more than that doesn't.
 */
class JwtAuthenticationFilterTest {

  private static final KeyPair KEY_PAIR = Jwts.SIG.RS256.keyPair().build();

  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(KEY_PAIR);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validTokenAuthenticatesTheRequestAsItsSubject() throws Exception {
    String userId = UUID.randomUUID().toString();
    String token = tokenFor(userId, Instant.now().plus(Duration.ofMinutes(10)));

    FilterChain filterChain = runFilter(token);

    // The filter let the request proceed to the rest of the chain...
    verify(filterChain).doFilter(any(), any());
    // ...and the authenticated principal is the token's own subject.
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo(userId);
  }

  @Test
  void tokenExpiredWithinClockSkewToleranceStillAuthenticates() throws Exception {
    String userId = UUID.randomUUID().toString();
    // 30s past exp — comfortably inside the 60s tolerance, with margin for
    // the RSA signing + JJWT parsing this test itself does taking a
    // non-zero amount of wall-clock time between "build" and "parse".
    String token = tokenFor(userId, Instant.now().minusSeconds(30));

    runFilter(token);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo(userId);
  }

  @Test
  void tokenExpiredBeyondClockSkewToleranceDoesNotAuthenticate() throws Exception {
    String userId = UUID.randomUUID().toString();
    // 90s past exp — comfortably outside the 60s tolerance.
    String token = tokenFor(userId, Instant.now().minusSeconds(90));

    runFilter(token);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private String tokenFor(String subject, Instant expiration) {
    return Jwts.builder()
        .subject(subject)
        .expiration(Date.from(expiration))
        .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  private FilterChain runFilter(String token) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);
    filter.doFilter(request, response, filterChain);
    return filterChain;
  }
}
