package com.cafefin.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 0.6 scaffold: proves integration tests can boot a real Postgres 17
 * in Docker and load the full Spring context against it, including Flyway
 * running V1__init.sql — instead of testing against an in-memory
 * substitute that wouldn't catch Postgres-specific SQL issues.
 */
@Testcontainers
@SpringBootTest
class CafefinApiApplicationTests {

  // static + shared across every test method in this class: Testcontainers
  // then starts the container once for the whole class instead of once per
  // test, since spinning up Postgres is the slow part. @ServiceConnection
  // auto-wires this container's JDBC URL/user/password into Spring's
  // DataSource (and, since none is set explicitly in test application.yml,
  // into Flyway too) — no manual @DynamicPropertySource needed.
  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));

  @Test
  void contextLoads() {
    // Intentionally empty: reaching this point already proves the full
    // context started, meaning Flyway applied V1__init.sql against the
    // containerized database and JPA connected successfully.
  }
}
