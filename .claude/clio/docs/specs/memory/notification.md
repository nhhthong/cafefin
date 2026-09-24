# Notification (Sync + Kafka Event-Driven)

## Decisions
- Layer 1 (synchronous): Mailpit in `docker-compose.yml` as dev/demo email sink.
  `NotificationProvider` interface backed by `MailpitEmailProvider` — abstraction boundary so a
  production provider can replace it without touching domain logic. Dispatch post-commit via
  `@Async` or `TransactionalEventListener(phase = AFTER_COMMIT)` — notification failure must never
  roll back a completed transfer. `NotificationLog` tracks dispatch history.
- Kafka infra: single-node Kafka 4.x, KRaft mode only. Topic `transaction.completed`, 3 partitions.
- Transactional Outbox (producer, in `cafefin-api`): `OutboxEvent` (`id`, `aggregatetype`,
  `aggregateid`, `type`, `payload`, `status` [PENDING/PUBLISHED], `createdAt`) — column names map
  to Debezium outbox standard. Persisted inside the same DB transaction as the transfer commit
  (local atomicity, no dual-write). Poller claims `PENDING` rows with
  `SELECT ... FOR UPDATE SKIP LOCKED LIMIT n`, transitions to `PROCESSING`, commits the claim
  *before* the Kafka network call (never hold DB locks open during the Kafka call); a later
  transaction marks `PUBLISHED`; crash recovery reclaims stale `PROCESSING` rows and must tolerate
  duplicate publication via idempotent consumers. Composite index `(status, createdAt)`. Message
  key = `accountId` (per-account ordering within partitions). Producer config:
  `enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection <= 5`.
- Consumer (`cafefin-notification`, extracted in Layer 3): `MailpitEmailProvider` + all delivery
  logic move out of `cafefin-api` here; `cafefin-api` retains only outbox publication after
  extraction. Consumer group subscribes to `transaction.completed`. Idempotent consumer via
  `processedEventId` persistence table — re-queued identical event ID skips dispatch.
- Ordering vs liveness: strict per-partition ordering is prioritized over throughput — blocking
  retries (not `@RetryableTopic`) paired with `DefaultErrorHandler` +
  `DeadLetterPublishingRecoverer` routing to `transaction.completed.dlt` after fixed, short retry
  backoffs.

## Open — ⚠️
(none)

## Source
> "**Migration rule**: Move `MailpitEmailProvider` and all delivery logic out of `cafefin-api` in
> this task; the API module retains only outbox event publication thereafter."
— cafefin_roadmap_v4.md, Task 1.6, 3.1–3.3
