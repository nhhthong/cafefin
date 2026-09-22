package com.cafefin.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code refresh_tokens} table (V3__refresh_tokens.sql). Stores a hash, never the raw
 * token — see {@link AuthService} for why.
 *
 * <p>{@code userId} is a plain UUID, not a {@code @ManyToOne User} relation: nothing here ever
 * navigates from a refresh token to the full {@code User} object graph, so a JPA relationship
 * (with its fetch-type/cascade decisions) would be mapping something never used.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean revoked;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  protected RefreshToken() {
    // JPA requires a no-arg constructor; not for direct use.
  }

  public RefreshToken(UUID userId, String tokenHash, Instant expiresAt, UUID familyId) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revoked = false;
    this.familyId = familyId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  /** Task 1.8.3.1: rotation marks the consumed token revoked instead of deleting the row. */
  public void revoke() {
    this.revoked = true;
  }
}
