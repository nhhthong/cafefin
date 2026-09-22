package com.cafefin.api.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  // Task 1.8.3.3: revoking a breach means revoking every token this rotation
  // chain ever issued, not just the one that got replayed.
  List<RefreshToken> findByFamilyId(UUID familyId);
}
