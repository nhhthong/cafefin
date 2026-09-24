package com.cafefin.api.auth;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Task 1.8.4.1: throttles {@code POST /api/v1/auth/login} per {@code (email, client IP)} pair —
 * memory/auth.md's chosen key. A bucket is created lazily per pair and holds {@link #LIMIT}
 * tokens that fully refill once every {@link #WINDOW} (not a gradual trickle) — an attacker who
 * exhausts the bucket must wait out the whole window, not just for one token back.
 *
 * <p>In-memory only ({@link Bucket}, not Bucket4j's distributed {@code ProxyManager}) — each
 * bucket lives in this JVM's heap. Valid only under the project's current single-API-instance
 * assumption (memory/auth.md); a second instance would double the effective limit since each
 * keeps its own map. The 5/minute threshold is this project's own pinned choice (confirmed with
 * the user at implementation time) — memory/auth.md and the plan deliberately leave it unspecified.
 */
@Component
public class LoginRateLimiter {

  private static final int LIMIT = 5;
  private static final Duration WINDOW = Duration.ofMinutes(1);

  // Grows for the JVM's lifetime — a demo-scope trade-off; a long-running
  // deployment would need idle-entry eviction or a distributed store instead.
  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public ConsumptionProbe tryConsume(String email, String clientIp) {
    Bucket bucket = buckets.computeIfAbsent(key(email, clientIp), unused -> newBucket());
    return bucket.tryConsumeAndReturnRemaining(1);
  }

  private static String key(String email, String clientIp) {
    return email + "|" + clientIp;
  }

  private static Bucket newBucket() {
    return Bucket.builder()
        .addLimit(limit -> limit.capacity(LIMIT).refillIntervally(LIMIT, WINDOW))
        .build();
  }
}
