package com.cafefin.api.auth;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/login}. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
