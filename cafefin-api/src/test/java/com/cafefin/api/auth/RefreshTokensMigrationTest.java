package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.1.4: proves the {@code V3__refresh_tokens.sql} migration applies and creates the exact
 * six columns task 1.1.4 promises.
 */
@Testcontainers
@SpringBootTest
class RefreshTokensMigrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void migrationCreatesRefreshTokensTableWithExpectedColumns() {
    // Reaching this point already proves Flyway applied V3 (context load would
    // otherwise fail) — this additionally confirms the exact column set.
    List<Map<String, Object>> columns =
        jdbcTemplate.queryForList(
            "select column_name from information_schema.columns "
                + "where table_name = 'refresh_tokens' order by ordinal_position");

    assertThat(columns.stream().map(row -> row.get("column_name")))
        .containsExactly("id", "user_id", "token_hash", "expires_at", "revoked", "family_id");
  }
}
