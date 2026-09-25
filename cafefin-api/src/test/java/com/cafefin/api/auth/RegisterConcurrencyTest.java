package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.1.3.1 (harden 1.1.1-1.1.3): concurrency.2 (double effect) — {@code register()} has no
 * check-then-insert (AuthService's own comment explains why), so the only thing standing between
 * "one row" and "two rows" for a raced identical request is the database's unique constraint. This
 * forces the race with a barrier rather than hoping two threads happen to overlap.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RegisterConcurrencyTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void concurrentRegistrationsSameEmailExactlyOneSucceeds() throws Exception {
    // A fresh email per run: the test is repeated 20 times by clio-test.sh,
    // and a shared email across runs would make every run after the first
    // see two already-taken emails instead of the race this test targets.
    String email = "race-" + System.nanoTime() + "@example.com";
    String body =
        objectMapper.writeValueAsString(new RegisterRequest(email, "some-password"));

    int threadCount = 2;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    Callable<Integer> attempt =
        () -> {
          barrier.await(); // both threads submit the identical request at the same instant
          return mockMvc
              .perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
              .andReturn()
              .getResponse()
              .getStatus();
        };

    try {
      List<Future<Integer>> futures = executor.invokeAll(List.of(attempt, attempt));
      List<Integer> statuses = List.of(futures.get(0).get(), futures.get(1).get());

      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    } finally {
      executor.shutdown();
    }
  }
}
