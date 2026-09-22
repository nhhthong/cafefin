package com.cafefin.api.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/auth/register}. Deliberately excludes
 * {@code passwordHash} — never one field of the {@link User} entity leaks
 * past this boundary.
 */
public record RegisterResponse(UUID id, String email, Instant createdAt) {

  static RegisterResponse from(User user) {
    return new RegisterResponse(user.getId(), user.getEmail(), user.getCreatedAt());
  }
}
