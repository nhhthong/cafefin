package com.cafefin.api.auth;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;

/**
 * Issues RS256-signed access tokens (memory/auth.md, JWT signing ADR). Verification (needed by
 * the JWT filter, task 1.1.8+) is deliberately not here yet — this class only covers what task
 * 1.1.5 needs.
 */
@Service
public class JwtService {

  // Spec range was 5-15 minutes; 10 was the pinned value (see the JWT signing ADR).
  private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(10);

  private final KeyPair keyPair;

  public JwtService(KeyPair jwtKeyPair) {
    this.keyPair = jwtKeyPair;
  }

  /**
   * {@code sub} is the user's id (a UUID), not their email — an email can change, an id can't,
   * and nothing downstream should have to re-look-up the user just to know which row this token
   * was issued for.
   */
  public String issueAccessToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
        .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }
}
