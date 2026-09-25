package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 1.1.2/1.1.3: {@code POST /api/v1/auth/register} with a new email
 * persists the user with a BCrypt-hashed password, never the plaintext, and
 * responds {@code 201} without leaking the hash back to the client. A second
 * registration for an already-used email is rejected with {@code 409}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RegisterEndpointTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void registerWithNewEmailReturns201AndHashesThePassword() throws Exception {
    String plaintextPassword = "correct horse battery staple";
    RegisterRequest request = new RegisterRequest("new-user@example.com", plaintextPassword);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", notNullValue()))
        .andExpect(jsonPath("$.email").value("new-user@example.com"))
        .andExpect(jsonPath("$.createdAt", notNullValue()))
        // The response DTO has no passwordHash field to begin with, but this
        // also guards against a future field rename accidentally exposing it.
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    User stored = userRepository.findByEmail("new-user@example.com").orElseThrow();
    assertThat(stored.getPasswordHash()).isNotEqualTo(plaintextPassword);
    assertThat(passwordEncoder.matches(plaintextPassword, stored.getPasswordHash())).isTrue();
  }

  @Test
  void registerWithAlreadyUsedEmailReturns409() throws Exception {
    RegisterRequest request = new RegisterRequest("duplicate@example.com", "some-password");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void registerWithInvalidEmailFormatReturns400() throws Exception {
    RegisterRequest request = new RegisterRequest("not-an-email", "some-password");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerWithBlankPasswordReturns400() throws Exception {
    RegisterRequest request = new RegisterRequest("blank-password@example.com", "");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
