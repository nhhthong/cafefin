package com.cafefin.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.ObjectMapper;

/**
 * Task 1.8.4.2: {@code POST /api/v1/auth/register} exceeding the configured Bucket4j limit for
 * one client IP returns {@code 429} with a {@code Retry-After} header and an RFC 9457
 * {@code application/problem+json} body.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RegisterRateLimitTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void exceedingTheRegisterLimitForOneClientIpReturns429WithRetryAfter() throws Exception {
    // Bucket capacity is 10 (RegisterRateLimiter) — a distinct email per
    // attempt so each of the first 10 succeeds on its own merits (201), and
    // the 11th is the one the rate limiter itself must reject regardless of
    // whether the email would otherwise be free to register.
    for (int attempt = 1; attempt <= 10; attempt++) {
      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new RegisterRequest(
                              "rate-limited-register-" + attempt + "@example.com",
                              "some-password"))))
          .andExpect(status().isCreated());
    }

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("rate-limited-register-11@example.com", "x"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
