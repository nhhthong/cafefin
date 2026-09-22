package com.cafefin.api.auth;

/** Response body for {@code POST /api/v1/auth/login} and {@code /refresh}. */
public record TokenPairResponse(String accessToken, String refreshToken) {}
