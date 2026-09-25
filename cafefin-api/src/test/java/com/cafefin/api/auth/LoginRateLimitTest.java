package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Task 1.8.4.1: {@code POST /api/v1/auth/login} exceeding the configured Bucket4j limit for one
 * {@code (email, client_ip)} pair returns {@code 429} with a {@code Retry-After} header and an
 * RFC 9457 {@code application/problem+json} body, and does not throttle a different key.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void exceedingTheLoginLimitForOnePairReturns429WithRetryAfter() throws Exception {
    String email = "rate-limited-user@example.com";
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(new RegisterRequest(email, "correct-password"))));

    // Bucket capacity is 5 (LoginRateLimiter) — the first 5 attempts for this
    // (email, client_ip) pair are let through to normal auth handling (401,
    // wrong password on purpose so this test needs no successful-login
    // bookkeeping); the 6th is the one the rate limiter itself must reject.
    for (int attempt = 1; attempt <= 5; attempt++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void aDifferentEmailFromTheSameClientIpIsNotThrottledByAnotherEmailsLimit() throws Exception {
    String exhaustedEmail = "exhausted-user@example.com";
    String otherEmail = "unaffected-user@example.com";
    for (String email : new String[] {exhaustedEmail, otherEmail}) {
      mockMvc.perform(
          post("/api/v1/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  objectMapper.writeValueAsString(
                      new RegisterRequest(email, "correct-password"))));
    }

    for (int attempt = 1; attempt <= 5; attempt++) {
      mockMvc.perform(
          post("/api/v1/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  objectMapper.writeValueAsString(
                      new LoginRequest(exhaustedEmail, "wrong-password"))));
    }
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest(exhaustedEmail, "wrong-password"))))
        .andExpect(status().isTooManyRequests());

    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest(otherEmail, "correct-password"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(responseBody).contains("accessToken");
  }
}
