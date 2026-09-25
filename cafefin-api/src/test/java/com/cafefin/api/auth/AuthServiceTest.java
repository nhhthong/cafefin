package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Task 1.1.3.1 (harden 1.1.1-1.1.3) / 1.1.10.1 (harden 1.1.4-1.1.10) / 1.8.3.3.1 (harden
 * 1.8.3.1-1.8.3.3): unit-level — {@link AuthService#register}, {@link AuthService#login} and
 * {@link AuthService#refresh} through mocked collaborators, no DB/HTTP involved. Real Postgres/HTTP
 * behaviour is {@link UserRepositoryIntegrationTest}'s, {@link LoginEndpointTest}'s and
 * {@link RefreshEndpointTest}'s job; this only proves AuthService's own branching.
 */
class AuthServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
  private final JwtService jwtService = mock(JwtService.class);
  private final AuthService authService =
      new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService);

  @Test
  void registerHashesPasswordBeforeSaving() {
    String plaintext = "correct horse battery staple";
    when(passwordEncoder.encode(plaintext)).thenReturn("bcrypt-hash");
    // save() normally returns the persisted entity (with a generated id); the
    // mock just echoes what it was given, which is enough to prove the hash
    // reached the repository, not the plaintext.
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User saved = authService.register(new RegisterRequest("new-user@example.com", plaintext));

    assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash").isNotEqualTo(plaintext);
    verify(passwordEncoder).encode(plaintext);
  }

  @Test
  void registerWithDuplicateEmailMapsTo409() {
    when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
    // The real unique-constraint violation only exists once the row hits
    // Postgres; here it's simulated at the seam AuthService actually depends
    // on, since this test never touches a database.
    when(userRepository.save(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key value"));

    assertThatThrownBy(
            () -> authService.register(new RegisterRequest("dup@example.com", "some-password")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void loginIssuesTokensForCorrectCredentials() {
    UUID userId = UUID.randomUUID();
    User user = new User("login@example.com", "bcrypt-hash");
    // id is normally Hibernate-generated on persist; this test never persists,
    // so it's set directly to give the assertions below something concrete.
    ReflectionTestUtils.setField(user, "id", userId);
    when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "bcrypt-hash")).thenReturn(true);
    when(jwtService.issueAccessToken(userId)).thenReturn("signed-access-token");
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TokenPairResponse response =
        authService.login(new LoginRequest("login@example.com", "correct-password"));

    assertThat(response.accessToken()).isEqualTo("signed-access-token");
    assertThat(response.refreshToken()).isNotBlank();

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(captor.capture());
    RefreshToken saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(userId);
    // Never the raw token (memory/auth.md) — SHA-256 hex is a fixed 64 chars
    // (V3__refresh_tokens.sql's own comment), checked without recomputing the
    // hash with the code's own algorithm.
    assertThat(saved.getTokenHash()).isNotEqualTo(response.refreshToken()).hasSize(64);
    assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    assertThat(saved.getFamilyId()).isNotNull();
  }

  @Test
  void loginWithNoMatchingUserMapsTo401() {
    when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("missing@example.com", "whatever")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void refreshWithValidTokenRotatesAndKeepsFamilyId() {
    UUID familyId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RefreshToken stored =
        new RefreshToken(userId, "old-hash", Instant.now().plus(Duration.ofDays(7)), familyId);
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
    when(jwtService.issueAccessToken(userId)).thenReturn("new-access-token");
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TokenPairResponse response = authService.refresh(new RefreshRequest("raw-old-token"));

    assertThat(response.accessToken()).isEqualTo("new-access-token");
    assertThat(stored.isRevoked()).isTrue();

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository, times(2)).save(captor.capture());
    List<RefreshToken> saved = captor.getAllValues();
    assertThat(saved.get(0)).isSameAs(stored);
    RefreshToken newRow = saved.get(1);
    assertThat(newRow.getFamilyId()).isEqualTo(familyId);
    assertThat(newRow.isRevoked()).isFalse();
    assertThat(newRow.getTokenHash()).isNotEqualTo(response.refreshToken());
  }

  @Test
  void refreshWithUnknownTokenMapsTo401() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh(new RefreshRequest("nope")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void refreshWithRevokedTokenRevokesFamilyAndMapsTo401() {
    UUID familyId = UUID.randomUUID();
    RefreshToken revokedRow =
        new RefreshToken(UUID.randomUUID(), "hash1", Instant.now().plus(Duration.ofDays(7)), familyId);
    revokedRow.revoke();
    RefreshToken siblingRow =
        new RefreshToken(UUID.randomUUID(), "hash2", Instant.now().plus(Duration.ofDays(7)), familyId);
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedRow));
    when(refreshTokenRepository.findByFamilyId(familyId))
        .thenReturn(List.of(revokedRow, siblingRow));

    assertThatThrownBy(() -> authService.refresh(new RefreshRequest("replayed")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    // The never-used sibling gets nuked too — that's the whole point of
    // family-wide revocation, not just the replayed token itself.
    assertThat(siblingRow.isRevoked()).isTrue();
    verify(refreshTokenRepository).saveAll(List.of(revokedRow, siblingRow));
  }

  @Test
  void refreshWithExpiredTokenDoesNotRevokeFamily() {
    UUID familyId = UUID.randomUUID();
    RefreshToken expiredRow =
        new RefreshToken(UUID.randomUUID(), "hash1", Instant.now().minusSeconds(1), familyId);
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredRow));

    assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    // Plain expiry is not a breach signal — only a replayed *revoked* token
    // is (memory/auth.md), so the family must be left untouched here.
    verify(refreshTokenRepository, never()).findByFamilyId(any());
    verify(refreshTokenRepository, never()).saveAll(any());
  }
}
