package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.1.1: proves the {@code V2__users.sql} migration actually creates the
 * table task 1.1.1 promises, and that the {@code email} UNIQUE constraint is
 * real (not just declared) — a race between two concurrent registrations for
 * the same email must be impossible for both to succeed, and only the
 * database, not application code, can guarantee that.
 */
@Testcontainers
@SpringBootTest
class UsersMigrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void migrationCreatesUsersTableWithExpectedColumns() {
    // Reaching this point already proves Flyway applied V2 successfully
    // (the context load would otherwise fail); this additionally checks the
    // exact column set task 1.1.1 specifies.
    List<Map<String, Object>> columns =
        jdbcTemplate.queryForList(
            "select column_name from information_schema.columns "
                + "where table_name = 'users' order by ordinal_position");

    assertThat(columns.stream().map(row -> row.get("column_name")))
        .containsExactly("id", "email", "password_hash", "created_at");
  }

  @Test
  void duplicateEmailViolatesUniqueConstraint() {
    jdbcTemplate.update(
        "insert into users (email, password_hash) values (?, ?)",
        "duplicate@example.com",
        "hash-1");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into users (email, password_hash) values (?, ?)",
                    "duplicate@example.com",
                    "hash-2"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
