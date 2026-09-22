package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
 * Task 1.1.5: {@code POST /api/v1/auth/login} with correct credentials returns an RS256-signed
 * access token (verifiable with the app's own public key, {@code exp} ≈ now + 10 min) and a
 * refresh token whose SHA-256 hash — never the raw value — is the only thing persisted.
 *
 * <p>Task 1.1.6/1.1.7: wrong password and non-existent email both reject with the exact same
 * {@code 401} body — an attacker can't use the response to tell "this email doesn't exist" apart
 * from "this email exists but the password is wrong".
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LoginEndpointTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private KeyPair jwtKeyPair;

  @Test
  void loginWithCorrectCredentialsReturnsAccessAndRefreshTokens() throws Exception {
    String email = "login-user@example.com";
    String password = "correct horse battery staple";
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RegisterRequest(email, password))));

    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(email, password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var node = objectMapper.readTree(responseBody);
    String accessToken = node.get("accessToken").asString();
    String refreshToken = node.get("refreshToken").asString();

    var claims =
        Jwts.parser()
            .verifyWith(jwtKeyPair.getPublic())
            .build()
            .parseSignedClaims(accessToken)
            .getPayload();
    assertThat(claims.getExpiration().toInstant())
        .isCloseTo(Instant.now().plus(Duration.ofMinutes(10)), within(Duration.ofSeconds(5)));

    List<RefreshToken> stored = refreshTokenRepository.findAll();
    assertThat(stored).hasSize(1);
    String expectedHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
    assertThat(stored.get(0).getTokenHash()).isEqualTo(expectedHash);
    assertThat(stored.get(0).getTokenHash()).isNotEqualTo(refreshToken);
  }

  @Test
  void loginWithWrongPasswordReturns401() throws Exception {
    String email = "wrong-password-user@example.com";
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(new RegisterRequest(email, "correct-password"))));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginWithUnknownEmailReturnsTheSame401BodyAsWrongPassword() throws Exception {
    String registeredEmail = "known-user@example.com";
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    new RegisterRequest(registeredEmail, "correct-password"))));

    String wrongPasswordBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest(registeredEmail, "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String unknownEmailBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest("never-registered@example.com", "anything"))))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Same endpoint path in both cases too, so a full body comparison (not
    // just a field-by-field one) is the strongest version of this check —
    // an attacker diffing the two raw responses sees zero difference.
    assertThat(unknownEmailBody).isEqualTo(wrongPasswordBody);
  }
}
