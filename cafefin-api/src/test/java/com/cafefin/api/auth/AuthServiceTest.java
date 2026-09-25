package com.cafefin.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Task 1.1.3.1 (harden 1.1.1-1.1.3): unit-level — {@link AuthService#register} through mocked
 * collaborators, no DB/HTTP involved. Real Postgres constraint behaviour is
 * {@link UserRepositoryIntegrationTest}'s job; this only proves AuthService's own branching.
 */
class AuthServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final AuthService authService =
      new AuthService(
          userRepository, mock(RefreshTokenRepository.class), passwordEncoder, mock(JwtService.class));

  @Test
  void registerHashesPasswordBeforeSaving() {
    String plaintext = "correct horse battery staple";
    when(passwordEncoder.encode(plaintext)).thenReturn("bcrypt-hash");
    // save() normally returns the persisted entity (with a generated id); the
    // mock just echoes what it was given, which is enough to prove the hash
    // reached the repository, not the plaintext.
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User saved = authService.register(new RegisterRequest("new-user@example.com", plaintext));

    assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash").isNotEqualTo(plaintext);
    verify(passwordEncoder).encode(plaintext);
  }

  @Test
  void registerWithDuplicateEmailMapsTo409() {
    when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
    // The real unique-constraint violation only exists once the row hits
    // Postgres; here it's simulated at the seam AuthService actually depends
    // on, since this test never touches a database.
    when(userRepository.save(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key value"));

    assertThatThrownBy(
            () -> authService.register(new RegisterRequest("dup@example.com", "some-password")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }
}
