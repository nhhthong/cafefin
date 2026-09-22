package com.cafefin.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Maps to the {@code users} table (V2__users.sql). Never leaves this package
 * as-is — controllers return dedicated response DTOs instead, so a JPA
 * entity (and its {@code passwordHash}) never crosses the HTTP boundary
 * (CLAUDE.md rule).
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  // GenerationType.UUID: Hibernate generates a random UUID in the JVM at
  // persist time, rather than relying on the column's DB-side
  // `gen_random_uuid()` default — both are equally valid, this one avoids a
  // round trip to read the generated value back after insert.
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected User() {
    // JPA requires a no-arg constructor; not for direct use.
  }

  public User(String email, String passwordHash) {
    this.email = email;
    this.passwordHash = passwordHash;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
