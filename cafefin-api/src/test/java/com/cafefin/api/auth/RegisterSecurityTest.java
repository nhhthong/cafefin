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
 * Task 1.1.3.1 (harden 1.1.1-1.1.3): security-level. Spring Data JPA parameterizes every query
 * behind {@link UserRepository}, so a SQL-syntax password can never reach raw SQL; this proves it
 * rather than assuming it. The second case proves a conflict response carries only the RFC 9457
 * shape (CLAUDE.md: never render internals in an error body).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RegisterSecurityTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  @Test
  void sqlInjectionInPasswordDoesNotExecute() throws Exception {
    String injectionAttempt = "'; DROP TABLE users; --";
    RegisterRequest request = new RegisterRequest("injection@example.com", injectionAttempt);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    // The users table surviving this call, plus the row actually being
    // there, is the proof: a successful injection would have dropped the
    // table before this read could run.
    assertThat(userRepository.findByEmail("injection@example.com")).isPresent();
  }

  @Test
  void conflictResponseLeaksNoInternalDetails() throws Exception {
    RegisterRequest request = new RegisterRequest("leak-check@example.com", "some-password");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body.toLowerCase())
        .doesNotContain("stacktrace")
        .doesNotContain("sql")
        .doesNotContain("hibernate")
        .doesNotContain("password");
  }
}
