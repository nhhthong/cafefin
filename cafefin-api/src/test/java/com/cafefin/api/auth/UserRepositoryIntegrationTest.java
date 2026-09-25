package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Task 1.1.3.1 (harden 1.1.1-1.1.3): integration-level — {@link UserRepository} against a real
 * Postgres, proving the write-then-read seam and the schema's own unique constraint, neither of
 * which {@link AuthServiceTest}'s mocks can exercise.
 */
@Testcontainers
@SpringBootTest
class UserRepositoryIntegrationTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private UserRepository userRepository;

  @Test
  void saveThenFindByEmailReturnsPersistedUser() {
    User saved = userRepository.save(new User("round-trip@example.com", "bcrypt-hash"));

    User found = userRepository.findByEmail("round-trip@example.com").orElseThrow();
    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getPasswordHash()).isEqualTo("bcrypt-hash");
  }

  @Test
  void saveWithDuplicateEmailViolatesUniqueConstraint() {
    userRepository.save(new User("dup-repo@example.com", "hash-1"));

    // saveAndFlush forces the insert to hit Postgres inside this test method
    // instead of batching at end-of-transaction, where JPA's first-level
    // cache would otherwise mask the constraint until commit.
    assertThatThrownBy(
            () -> userRepository.saveAndFlush(new User("dup-repo@example.com", "hash-2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
