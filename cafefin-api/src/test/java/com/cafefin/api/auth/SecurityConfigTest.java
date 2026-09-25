package com.cafefin.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 1.1.8: a request with no {@code Authorization} header to any route other than
 * {@code /register}/{@code /login}/{@code /actuator/health} is rejected with {@code 401} — by
 * {@link SecurityConfig}'s authorization rule alone. No real business endpoint is needed to prove
 * this: Spring Security's filter chain runs (and can reject the request) before Spring MVC ever
 * looks for a matching {@code @RequestMapping}, so an unmapped path behind the same rule proves
 * the same thing a real protected endpoint would.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Container
  @ServiceConnection
  @SuppressWarnings("resource") // false-positive: chained .withReuse(true) confuses JDT's resource-leak check
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11")).withReuse(true);

  @Autowired private MockMvc mockMvc;

  @Test
  void requestWithNoAuthorizationHeaderToAProtectedRouteReturns401() throws Exception {
    // Spring Security's own default entry point would send this 401 with an
    // empty body — ProblemDetailAuthenticationEntryPoint keeps it on the
    // same RFC 9457 shape every other error in this app uses.
    mockMvc
        .perform(get("/api/v1/anything"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value("/api/v1/anything"));
  }
}
