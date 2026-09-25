package com.cafefin.api.auth;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  // PESSIMISTIC_WRITE: refresh() reads this row then conditionally writes to
  // it, all inside one @Transactional method (task 1.8.3.3.1) — without a
  // row lock, two concurrent refreshes of the same token both read
  // revoked=false and both rotate it (found by RefreshConcurrencyTest).
  // The lock only holds for the duration of the caller's transaction.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  // Task 1.8.3.3: revoking a breach means revoking every token this rotation
  // chain ever issued, not just the one that got replayed.
  List<RefreshToken> findByFamilyId(UUID familyId);
}
