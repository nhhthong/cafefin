package com.cafefin.api.auth;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Task 1.8.4.2: throttles {@code POST /api/v1/auth/register} per client IP — memory/auth.md's
 * chosen key (no email to pair it with, unlike login, since a registration attempt is the first
 * time this email is ever seen). Same shape as {@link LoginRateLimiter}: see that class for the
 * in-memory/single-instance caveat, which applies here too.
 *
 * <p>Threshold: 10/minute, looser than login's 5/minute — this project's own pinned choice
 * (confirmed with the user at implementation time, memory/auth.md leaves it a placeholder). An
 * IP can be shared by many real users behind NAT/a proxy, and a registration attempt is cheaper
 * to recover from than a login one (no account to lock an attacker out of), so a wrong guess here
 * costs less than 1.8.4.1's tighter per-account limit.
 */
@Component
public class RegisterRateLimiter {

  private static final int LIMIT = 10;
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public ConsumptionProbe tryConsume(String clientIp) {
    Bucket bucket = buckets.computeIfAbsent(clientIp, unused -> newBucket());
    return bucket.tryConsumeAndReturnRemaining(1);
  }

  private static Bucket newBucket() {
    return Bucket.builder()
        .addLimit(limit -> limit.capacity(LIMIT).refillIntervally(LIMIT, WINDOW))
        .build();
  }
}
