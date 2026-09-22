package com.cafefin.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  // memory/auth.md states no number for this; 7 days was the user's own pinned
  // value (see the JWT signing ADR — no roadmap source for this one).
  private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
  private static final int REFRESH_TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  /**
   * Task 1.1.2/1.1.3: hashes and persists a new user. Duplicate email is
   * caught at the database's unique constraint (V2__users.sql), not by a
   * check-then-insert — a check first would race two concurrent
   * registrations for the same email, and the constraint is the only thing
   * that actually makes that race impossible to both succeed.
   */
  public User register(RegisterRequest request) {
    User user = new User(request.email(), passwordEncoder.encode(request.password()));
    try {
      return userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      // Boot's problemdetails support (spring.mvc.problemdetails.enabled)
      // renders ResponseStatusException as RFC 9457 automatically — no
      // custom exception type or @ControllerAdvice needed for this case.
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered", e);
    }
  }

  /**
   * Task 1.1.5: correct credentials → an access token plus a fresh refresh token. Task 1.1.6/1.1.7
   * (wrong password and unknown email must look identical to the caller) fall out of this same
   * {@code findByEmail(...).filter(passwordMatches).orElseThrow(...)} chain for free: a missing
   * user and a present-but-wrong-password user both reach the same {@code orElseThrow}, so there's
   * only one 401 shape to begin with — no separate enumeration-safety code path to keep in sync.
   */
  public TokenPairResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

    String accessToken = jwtService.issueAccessToken(user.getId());

    String rawRefreshToken = generateRawToken();
    RefreshToken refreshToken =
        new RefreshToken(
            user.getId(),
            hashToken(rawRefreshToken),
            Instant.now().plus(REFRESH_TOKEN_TTL),
            // A fresh family per login — task 1.8.3's rotation chain (and its
            // breach detection) starts here and extends on each /refresh.
            UUID.randomUUID());
    refreshTokenRepository.save(refreshToken);

    return new TokenPairResponse(accessToken, rawRefreshToken);
  }

  /**
   * Task 1.8.3.1/1.8.3.3: rotates a valid refresh token — the consumed row is marked revoked
   * (never deleted, so a later replay has something to find), and a new pair is issued keeping the
   * same {@code family_id}. Replaying an already-revoked token is the actual breach signal: the
   * only way a client can present a token that was already consumed is if someone else got a copy
   * of it after it was rotated away — so every token in that family gets revoked, not just the one
   * that was replayed. An unknown or expired token gets the same plain {@code 401}; there's no
   * family to distrust when the token was never valid to begin with.
   */
  public TokenPairResponse refresh(RefreshRequest request) {
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(hashToken(request.refreshToken()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));

    if (stored.isRevoked()) {
      revokeFamily(stored.getFamilyId());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
    }
    if (stored.getExpiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
    }

    stored.revoke();
    refreshTokenRepository.save(stored);

    String accessToken = jwtService.issueAccessToken(stored.getUserId());

    String rawRefreshToken = generateRawToken();
    RefreshToken newRefreshToken =
        new RefreshToken(
            stored.getUserId(),
            hashToken(rawRefreshToken),
            Instant.now().plus(REFRESH_TOKEN_TTL),
            stored.getFamilyId());
    refreshTokenRepository.save(newRefreshToken);

    return new TokenPairResponse(accessToken, rawRefreshToken);
  }

  private void revokeFamily(UUID familyId) {
    List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);
    family.forEach(RefreshToken::revoke);
    refreshTokenRepository.saveAll(family);
  }

  private String generateRawToken() {
    byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  // SHA-256, not BCrypt: memory/auth.md is explicit that refresh tokens are
  // high-entropy random values, not low-entropy passwords, so bcrypt's
  // slow-by-design hashing buys nothing here and only adds cost per lookup.
  private String hashToken(String rawToken) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // Every JDK ships SHA-256 (mandated by the Java security provider
      // spec) — this can't actually happen.
      throw new IllegalStateException(e);
    }
  }
}
