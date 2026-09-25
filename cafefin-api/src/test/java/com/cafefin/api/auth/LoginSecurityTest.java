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
 * Task 1.1.10.1 (harden 1.1.4-1.1.10): security.5 — a failed login's {@code 401} body carries
 * only the RFC 9457 shape (CLAUDE.md: never render internals in an error body), the same
 * principle {@link RegisterSecurityTest} proves for register's {@code 409}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LoginSecurityTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void loginFailureResponseLeaksNoInternalDetails() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest("never-registered-2@example.com", "anything"))))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body.toLowerCase())
        .doesNotContain("stacktrace")
        .doesNotContain("sql")
        .doesNotContain("hibernate")
        .doesNotContain("password")
        .doesNotContain("bcrypt");
  }
}
