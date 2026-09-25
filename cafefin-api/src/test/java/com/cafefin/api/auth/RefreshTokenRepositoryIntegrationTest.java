package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.1.10.1 (harden 1.1.4-1.1.10) / 1.8.3.3.1 (harden 1.8.3.1-1.8.3.3): integration-level —
 * {@link RefreshTokenRepository} against a real Postgres, proving the write-then-read seam
 * {@link AuthService#login}/{@code refresh} depend on, and the schema's constraints
 * ({@code V3__refresh_tokens.sql}: the {@code user_id REFERENCES users(id)} foreign key and the
 * {@code token_hash} unique index) that neither {@link AuthServiceTest}'s mocks can exercise.
 */
@Testcontainers
@SpringBootTest
class RefreshTokenRepositoryIntegrationTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Test
  void saveThenFindByTokenHashReturnsPersistedToken() {
    User user = userRepository.save(new User("refresh-repo-owner@example.com", "bcrypt-hash"));
    RefreshToken token =
        new RefreshToken(
            user.getId(), "a".repeat(64), Instant.now().plus(7, ChronoUnit.DAYS), UUID.randomUUID());

    refreshTokenRepository.save(token);

    RefreshToken found = refreshTokenRepository.findByTokenHash("a".repeat(64)).orElseThrow();
    assertThat(found.getUserId()).isEqualTo(user.getId());
    assertThat(found.isRevoked()).isFalse();
  }

  @Test
  void saveWithUnknownUserIdViolatesForeignKey() {
    RefreshToken token =
        new RefreshToken(
            UUID.randomUUID(), // no user with this id exists
            "b".repeat(64),
            Instant.now().plus(7, ChronoUnit.DAYS),
            UUID.randomUUID());

    // saveAndFlush forces the insert to hit Postgres inside this test method,
    // same reasoning as UserRepositoryIntegrationTest's constraint case.
    assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(token))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByFamilyIdReturnsEveryRowInTheFamily() {
    User user = userRepository.save(new User("family-owner@example.com", "bcrypt-hash"));
    UUID familyId = UUID.randomUUID();
    RefreshToken first =
        refreshTokenRepository.save(
            new RefreshToken(
                user.getId(), "c".repeat(64), Instant.now().plus(7, ChronoUnit.DAYS), familyId));
    RefreshToken second =
        refreshTokenRepository.save(
            new RefreshToken(
                user.getId(), "d".repeat(64), Instant.now().plus(7, ChronoUnit.DAYS), familyId));

    List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);

    assertThat(family).extracting(RefreshToken::getId).containsExactlyInAnyOrder(
        first.getId(), second.getId());
  }

  @Test
  void revokeThenSavePersistsAcrossReload() {
    User user = userRepository.save(new User("revoke-owner@example.com", "bcrypt-hash"));
    RefreshToken token =
        refreshTokenRepository.save(
            new RefreshToken(
                user.getId(), "e".repeat(64), Instant.now().plus(7, ChronoUnit.DAYS), UUID.randomUUID()));

    token.revoke();
    refreshTokenRepository.save(token);

    RefreshToken reloaded = refreshTokenRepository.findById(token.getId()).orElseThrow();
    assertThat(reloaded.isRevoked()).isTrue();
  }

  @Test
  void saveWithDuplicateTokenHashViolatesUniqueConstraint() {
    User user = userRepository.save(new User("hash-collision@example.com", "bcrypt-hash"));
    refreshTokenRepository.save(
        new RefreshToken(
            user.getId(), "f".repeat(64), Instant.now().plus(7, ChronoUnit.DAYS), UUID.randomUUID()));

    RefreshToken sameHash =
        new RefreshToken(
            user.getId(), "f".repeat(64), Instant.now().plus(7, ChronoUnit.DAYS), UUID.randomUUID());

    assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(sameHash))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
