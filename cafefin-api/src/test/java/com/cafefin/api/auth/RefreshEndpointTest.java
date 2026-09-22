package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
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
 * Task 1.8.3.1: {@code POST /api/v1/auth/refresh} with a valid, unrevoked, unexpired token rotates
 * it — the old row is marked revoked (not deleted), a new pair is issued, and the new refresh
 * token keeps the same {@code family_id} as the one it replaced.
 *
 * <p>Task 1.8.3.2: an expired refresh token is rejected the same way an unknown one would be.
 *
 * <p>Task 1.8.3.3: replaying a token that was already rotated away (a breach signal) revokes every
 * token sharing its {@code family_id} — including ones never used yet — not just the replayed one.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RefreshEndpointTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Test
  void refreshWithValidTokenRotatesAndKeepsFamilyId() throws Exception {
    String email = "refresh-user@example.com";
    String password = "correct horse battery staple";
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RegisterRequest(email, password))));

    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String oldRawRefreshToken = objectMapper.readTree(loginBody).get("refreshToken").asString();
    RefreshToken oldRow = onlyRowFor(oldRawRefreshToken);
    UUID familyId = oldRow.getFamilyId();

    String refreshBody =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RefreshRequest(oldRawRefreshToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRawRefreshToken = objectMapper.readTree(refreshBody).get("refreshToken").asString();

    assertThat(newRawRefreshToken).isNotEqualTo(oldRawRefreshToken);

    RefreshToken reloadedOldRow =
        refreshTokenRepository.findById(oldRow.getId()).orElseThrow();
    assertThat(reloadedOldRow.isRevoked()).isTrue();

    RefreshToken newRow = onlyRowWithHashOf(newRawRefreshToken);
    assertThat(newRow.isRevoked()).isFalse();
    assertThat(newRow.getFamilyId()).isEqualTo(familyId);
  }

  @Test
  void refreshWithExpiredTokenReturns401() throws Exception {
    String email = "expired-refresh-user@example.com";
    String registerBody =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterRequest(email, "some-password"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID userId = UUID.fromString(objectMapper.readTree(registerBody).get("id").asString());

    // Seed an already-expired row directly — no need to wait 7 real days.
    String rawToken = "expired-token-for-test";
    RefreshToken expired =
        new RefreshToken(userId, hashOf(rawToken), Instant.now().minusSeconds(1), UUID.randomUUID());
    refreshTokenRepository.save(expired);

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(rawToken))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void replayingARotatedTokenRevokesTheWholeFamily() throws Exception {
    String email = "breach-user@example.com";
    String password = "correct horse battery staple";
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RegisterRequest(email, password))));

    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstRawRefreshToken = objectMapper.readTree(loginBody).get("refreshToken").asString();
    UUID familyId = onlyRowFor(firstRawRefreshToken).getFamilyId();

    // Legitimate rotation: first token -> second token, same family.
    String refreshBody =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new RefreshRequest(firstRawRefreshToken))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondRawRefreshToken =
        objectMapper.readTree(refreshBody).get("refreshToken").asString();

    // Replay of the now-revoked first token: the breach case.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new RefreshRequest(firstRawRefreshToken))))
        .andExpect(status().isUnauthorized());

    List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);
    assertThat(family).hasSize(2);
    assertThat(family).allMatch(RefreshToken::isRevoked);

    // The second (legitimately rotated, never-used) token is now unusable too.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new RefreshRequest(secondRawRefreshToken))))
        .andExpect(status().isUnauthorized());
  }

  private RefreshToken onlyRowFor(String rawToken) {
    return onlyRowWithHashOf(rawToken);
  }

  private RefreshToken onlyRowWithHashOf(String rawToken) {
    String hash = hashOf(rawToken);
    return refreshTokenRepository.findByTokenHash(hash).orElseThrow();
  }

  private String hashOf(String rawToken) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
