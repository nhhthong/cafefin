package com.cafefin.api.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Task 1.1.2/1.1.3: hashes and persists a new user. Duplicate email is
   * caught at the database's unique constraint (V2__users.sql), not by a
   * check-then-insert — a check first would race two concurrent
   * registrations for the same email, and the constraint is the only thing
   * that actually makes that race impossible to both succeed.
   */
  public User register(RegisterRequest request) {
    User user = new User(request.email(), passwordEncoder.encode(request.password()));
    try {
      return userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      // Boot's problemdetails support (spring.mvc.problemdetails.enabled)
      // renders ResponseStatusException as RFC 9457 automatically — no
      // custom exception type or @ControllerAdvice needed for this case.
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered", e);
    }
  }
}
