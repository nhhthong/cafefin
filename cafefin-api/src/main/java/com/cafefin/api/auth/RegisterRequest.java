package com.cafefin.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/register}. */
public record RegisterRequest(
    @NotBlank @Email String email,
    // No length/complexity policy is stated in memory/auth.md — only
    // non-blank is enforced here; anything stricter would be an invented
    // number, not a spec-derived one.
    @NotBlank String password) {}
