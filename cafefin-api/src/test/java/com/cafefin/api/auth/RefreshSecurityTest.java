package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.8.3.3.1 (harden 1.8.3.1-1.8.3.3): security-level. A garbage {@code refreshToken} must
 * fail the same way an unknown one does (401, not a 500) — the hash lookup itself never throws on
 * an arbitrary string. A failed refresh's body carries only the RFC 9457 shape (CLAUDE.md), same
 * principle {@link RegisterSecurityTest}/{@link LoginSecurityTest} prove for their own endpoints.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RefreshSecurityTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void garbageRefreshTokenDoesNotCauseError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new RefreshRequest("not-a-real-token-at-all"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshFailureResponseLeaksNoInternalDetails() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new RefreshRequest("unknown-token-2"))))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body.toLowerCase())
        .doesNotContain("stacktrace")
        .doesNotContain("sql")
        .doesNotContain("hibernate")
        .doesNotContain("hash");
  }
}
