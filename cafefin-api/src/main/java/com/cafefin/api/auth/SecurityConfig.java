package com.cafefin.api.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Task 1.1.8: without this bean, Spring Boot's own default security config would require a
 * (browser) login for every endpoint, including {@code /register} and {@code /login} themselves —
 * this replaces that default with rules that fit a stateless, bearer-token REST API.
 */
@Configuration
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      ProblemDetailAuthenticationEntryPoint authenticationEntryPoint) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // CSRF defends against a browser sending a victim's *cookie*
        // somewhere it didn't mean to. There's no session cookie here —
        // every request carries its own bearer token explicitly — so
        // there's nothing for CSRF protection to defend.
        .csrf(csrf -> csrf.disable())
        // No HttpSession is ever created or read; each request is
        // authenticated fresh from its own Authorization header.
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // Spring Security's own fallback (when neither formLogin() nor
        // httpBasic() is configured) isn't guaranteed to be 401, and its
        // built-in HttpStatusEntryPoint sends an empty body — this app's
        // ProblemDetailAuthenticationEntryPoint pins the status to 401 AND
        // keeps the same RFC 9457 body shape every other error uses.
        .exceptionHandling(
            exceptionHandling -> exceptionHandling.authenticationEntryPoint(authenticationEntryPoint))
        // Runs before Spring Security's own form-login filter so a valid
        // bearer token authenticates the request before anything else in
        // the chain gets a chance to reject it as anonymous.
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
