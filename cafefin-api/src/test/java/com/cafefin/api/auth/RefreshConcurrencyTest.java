package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.8.3.3.1 (harden 1.8.3.1-1.8.3.3): concurrency.2 (double effect) — {@code refresh()} being
 * {@code @Transactional} (task 1.8.3.3.1's own atomicity fix) makes each call internally
 * consistent, but does not by itself serialize two *concurrent* calls against the same row:
 * Postgres' default READ_COMMITTED isolation lets two transactions both read {@code revoked=false}
 * before either writes. This forces that race with a barrier rather than hoping two threads overlap.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RefreshConcurrencyTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void concurrentRefreshWithSameTokenExactlyOneSucceeds() throws Exception {
    String email = "refresh-race-" + System.nanoTime() + "@example.com";
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
    String rawRefreshToken = objectMapper.readTree(loginBody).get("refreshToken").asString();
    // Plain JDBC, not the repository's own findByTokenHash: that method now
    // takes a PESSIMISTIC_WRITE lock (this task's concurrency fix), which
    // requires an active transaction — this setup read has none.
    UUID familyId =
        jdbcTemplate.queryForObject(
            "select family_id from refresh_tokens where token_hash = ?",
            UUID.class,
            hashOf(rawRefreshToken));
    String body = objectMapper.writeValueAsString(new RefreshRequest(rawRefreshToken));

    int threadCount = 2;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    Callable<Integer> attempt =
        () -> {
          barrier.await(); // both threads submit the identical refresh request at once
          return mockMvc
              .perform(
                  post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
              .andReturn()
              .getResponse()
              .getStatus();
        };

    try {
      List<Future<Integer>> futures = executor.invokeAll(List.of(attempt, attempt));
      List<Integer> statuses = List.of(futures.get(0).get(), futures.get(1).get());

      // The lock (this task's fix) guarantees exactly one thread wins the
      // rotation (200) and the other is rejected (401) — concurrency.2's own
      // invariant. Without the lock both threads got 200 (double-issue bug
      // this test originally caught).
      assertThat(statuses).containsExactlyInAnyOrder(200, 401);

      // The loser presents a token that, by the time its blocked transaction
      // proceeds, is already revoked (the winner committed first) — from the
      // server's side that's indistinguishable from a real stolen-token
      // replay, so it correctly triggers the same family-wide revocation
      // (memory/auth.md's breach-detection policy applies uniformly, user
      // confirmed no race-vs-theft exception): the winner's own brand-new
      // token gets revoked too, not just the pre-race row.
      List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);
      assertThat(family).hasSize(2);
      assertThat(family).allMatch(RefreshToken::isRevoked);
    } finally {
      executor.shutdown();
    }
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
