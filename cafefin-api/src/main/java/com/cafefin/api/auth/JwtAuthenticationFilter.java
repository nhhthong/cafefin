package com.cafefin.api.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyPair;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Task 1.1.9/1.1.10: a valid {@code Authorization: Bearer <token>} header authenticates the
 * request — the token's {@code sub} claim (the user's id, set by
 * {@link JwtService#issueAccessToken}) becomes the {@code SecurityContext} principal. A token
 * whose {@code exp} is up to {@link #CLOCK_SKEW_SECONDS} in the past is still accepted — the
 * server that signed it and the server verifying it are never perfectly clock-synced (NTP drift
 * is normal), so a strictly exact comparison would reject tokens that are still "morally" valid
 * (see the JWT signing ADR for the pinned value).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final long CLOCK_SKEW_SECONDS = 60;

  private final KeyPair jwtKeyPair;

  public JwtAuthenticationFilter(KeyPair jwtKeyPair) {
    this.jwtKeyPair = jwtKeyPair;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      try {
        String subject =
            Jwts.parser()
                .verifyWith(jwtKeyPair.getPublic())
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(header.substring(BEARER_PREFIX.length()))
                .getPayload()
                .getSubject();
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(subject, null, List.of()));
      } catch (JwtException e) {
        // Invalid, expired, or malformed: leave SecurityContext empty.
        // SecurityConfig's authorizeHttpRequests rule then rejects the
        // request with 401 the same way a missing header does (task 1.1.8)
        // — no separate error handling needed here.
      }
    }
    filterChain.doFilter(request, response);
  }
}
