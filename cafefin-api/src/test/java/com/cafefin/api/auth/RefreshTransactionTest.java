package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.8.3.3.1 (harden 1.8.3.1-1.8.3.3): integration-level — {@link AuthService#refresh} makes
 * two writes (revoke the old token, then persist the new pair). {@code jwtService} is swapped for
 * a mock that fails between them, at the real seam {@code refresh()} calls it at — no other way to
 * force the second write to fail deterministically through the public API. Proves the two writes
 * are atomic (integration.1: "the transaction rolls back on failure and leaves no partial write").
 *
 * <p>{@code @MockitoBean}, not the older {@code @MockBean}: Boot 4.1.1/Spring Framework 7 removed
 * {@code @MockBean} — verified absent from {@code spring-boot-test-4.1.1.jar}.
 */
@Testcontainers
@SpringBootTest
class RefreshTransactionTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private AuthService authService;

  @MockitoBean private JwtService jwtService;

  @Test
  void refreshRollsBackWhenIssuingNewTokenFails() {
    User user = userRepository.save(new User("txn-rollback@example.com", "bcrypt-hash"));
    String rawToken = "raw-token-for-txn-test";
    RefreshToken stored =
        refreshTokenRepository.save(
            new RefreshToken(
                user.getId(), hashOf(rawToken), Instant.now().plus(7, ChronoUnit.DAYS), UUID.randomUUID()));

    when(jwtService.issueAccessToken(user.getId()))
        .thenThrow(new RuntimeException("simulated jwt failure"));

    assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
        .isInstanceOf(RuntimeException.class);

    // The old token's revoke() call happens before the failing jwtService
    // call — if refresh() isn't transactional, that first write already
    // committed and the row stays stuck revoked with no replacement issued.
    RefreshToken reloaded = refreshTokenRepository.findById(stored.getId()).orElseThrow();
    assertThat(reloaded.isRevoked()).isFalse();
  }

  private String hashOf(String rawToken) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
