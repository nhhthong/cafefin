package com.cafefin.api.auth;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The RSA key pair {@link JwtService} signs/verifies access tokens with (RS256 — see the JWT
 * signing ADR). Generated fresh at JVM startup, held only in memory — this is a demo project's
 * simplification: no key persists across a restart, so every access token issued before one
 * becomes unverifiable after it. A real deployment would load a long-lived key pair from a KMS or
 * a mounted secret instead of generating one at boot.
 */
@Configuration
public class JwtConfig {

  @Bean
  public KeyPair jwtKeyPair() {
    return Jwts.SIG.RS256.keyPair().build();
  }
}
