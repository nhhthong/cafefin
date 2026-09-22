package com.cafefin.api.auth;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/refresh}. */
public record RefreshRequest(@NotBlank String refreshToken) {}
