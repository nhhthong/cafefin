[← Back to docs index](../TUTORIAL.md)

# Flow: Register (`POST /api/v1/auth/register`)

Creates a new user account. Public endpoint — no auth required.

---

## 1. Contract

- **Method & path**: `POST /api/v1/auth/register`
- **Auth**: none
- **Rate limit**: 10 attempts / 1 minute, keyed by client IP — exceeding it returns `429` before
  the email/password are even validated (§ 4)
- **Request / response**: JSON

### Request

```json
{
  "email": "learner@example.com",
  "password": "correcthorsebattery"
}
```

### Response — `201 Created`

```json
{
  "id": "0feea416-035a-439b-beac-8719d9db289c",
  "email": "learner@example.com",
  "createdAt": "2026-09-22T03:32:43.641002Z"
}
```

`id` is a random UUID, generated in the JVM by Hibernate (`GenerationType.UUID`), not read back
from the database's own `gen_random_uuid()` default — and not a sequential `1, 2, 3, ...` id, on
purpose: a sequential id would let anyone who sees one user's id guess roughly how many users exist
and how fast that number is growing. The password hash is never returned — `RegisterResponse`
simply has no field for it (§ 3, step 8 has the full reasoning).

---

## 2. Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant H as AuthController.register
    participant L as RegisterRateLimiter
    participant V as Bean Validation (@Valid)
    participant S as AuthService.register
    participant E as BCryptPasswordEncoder
    participant R as UserRepository
    participant DB as PostgreSQL (users)

    Client->>H: POST /api/v1/auth/register
    H->>L: tryConsume(clientIp)

    alt bucket for clientIp is exhausted
        L-->>H: not consumed
        H-->>Client: 429 "too many registration attempts, try again later"
    else token available
        L-->>H: consumed
        H->>V: validate RegisterRequest

        alt body is not valid JSON
            V-->>Client: 400 "Failed to read request"
        else email blank / not well-formed, or password blank
            V-->>Client: 400 "Invalid request content."
        end

        H->>S: register(request)
        S->>E: encode(password)
        E-->>S: BCrypt hash
        S->>R: save(new User(email, hash))
        R->>DB: INSERT INTO users (id, email, password_hash, created_at)

        alt email already registered
            DB-->>R: 23505 unique_violation on users_email_key
            R-->>S: DataIntegrityViolationException
            S-->>H: ResponseStatusException(409)
            H-->>Client: 409 "email already registered"
        else
            DB-->>R: ok — id and created_at generated
        end

        R-->>S: User
        S-->>H: User
        H-->>Client: 201 Created {id, email, createdAt}
    end
```

---

## 3. Step by step

1. **Spend one rate-limit token first — before validation, before touching the database.**
   `RegisterRateLimiter.tryConsume(clientIp)` — a Bucket4j `Bucket` per client IP, capacity 10,
   refilling all at once every 1 minute. Unlike login, there's no email to pair the key with: a
   registration attempt is the first time this email is ever seen, so IP alone is what's available.
   That's also why the limit is looser than login's 5/minute — an IP can be shared by many real
   users behind NAT or a proxy, and a bad registration attempt is cheaper to recover from than a bad
   login attempt (no account to lock an attacker out of). Buckets are an in-memory map, same
   single-instance caveat as login's limiter.
2. **Deserialize + validate.** `@Valid @RequestBody RegisterRequest` — `email` must be non-blank and
   a well-formed address (`@Email`), `password` must be non-blank (`@NotBlank`). This is *declarative*
   validation: the annotations describe the rule, and Spring runs the check before
   `AuthController.register`'s body executes at all — the method never sees an invalid `email` or
   `password`, so it doesn't need an `if` for either. Malformed JSON and failed validation both
   surface as Spring's default `400` `ProblemDetail`, not a custom error shape — see § 4 for the
   exact bodies.
3. **No email normalization.** Unlike a case-insensitive lookup, the `email` column's `UNIQUE`
   constraint is case-sensitive as stored — `A@x.com` and `a@x.com` are two different rows today.
   No lower-casing/trimming happens in `RegisterRequest` or `AuthService`.
4. **No password policy beyond non-blank.** The spec states none, so none is enforced —
   deliberately not inventing a length/complexity rule that isn't actually decided anywhere.
5. **Hash the password.** `BCryptPasswordEncoder` (default strength, cost 10) turns the plaintext
   into a hash. This is the one step worth understanding deeply if you're new to auth: bcrypt is a
   **one-way** function — there is no `decode()`. Nothing in this codebase, not even an admin, can
   ever recover the original password from what's stored in `password_hash`. That's also why
   `POST /api/v1/auth/login` can only *compare* (`passwordEncoder.matches(rawPassword, storedHash)` —
   re-hash the login attempt and check it matches), never "look up and decrypt" the password. bcrypt
   also generates its own random salt and embeds it inside the hash string it returns, so there's no
   separate salt column to manage.
6. **No pre-check for duplicates.** `AuthService.register` goes straight to `save()` and lets the
   `users.email` `UNIQUE` constraint (`V2__users.sql`) do the work, instead of querying "does this
   email exist?" first. That query-then-insert shape has a real bug hiding in it: two requests for
   the same brand-new email can both run the "does it exist?" check *before* either one has
   inserted anything, both see "no", and both proceed to insert — the check and the insert aren't
   one atomic operation. A database constraint is checked by the database itself at insert time, so
   it's the only thing here that can't be raced.
7. **Catch and translate.** A `DataIntegrityViolationException` from that constraint is caught and
   rethrown as `ResponseStatusException(HttpStatus.CONFLICT, "email already registered")`. Spring's
   `problemdetails` support (`spring.mvc.problemdetails.enabled: true`) renders it as RFC 9457
   automatically — no custom exception type or `@ControllerAdvice`. The reason this project uses a
   structured error format at all, rather than a plain string: the caller of a fintech API is
   usually code (a frontend, or another backend service), not a human reading a screen — it needs a
   `status`/`detail` it can branch on programmatically, not prose to parse.
8. **Respond with a DTO, not the entity.** `RegisterResponse` is a separate class from `User` with
   only `id`, `email`, `createdAt` — `User` (the `@Entity`, which also holds `passwordHash`) never
   gets serialized to JSON directly. This isn't just about hiding the password: it also means the
   database schema and the public API shape are free to evolve independently — a column can be
   renamed or added to `users` without silently changing what `POST /register` returns. Registration
   does not log the user in — the client still has to call `POST /api/v1/auth/login` separately.

---

## 4. Errors

Bodies below are the real responses (`curl` and `RegisterRateLimitTest`), not invented — Spring's
default `ProblemDetail` for validation/media-type errors is generic (`"Invalid request content."`),
it does not list which field failed.

| Condition | Status | `detail` |
|---|:---:|---|
| Body is not valid JSON | `400` | `Failed to read request` |
| `email` blank/not a valid address, or `password` blank | `400` | `Invalid request content.` |
| `Content-Type` isn't `application/json` | `415` | `Content-Type '...' is not supported.` |
| Email already registered | `409` | `email already registered` |
| More than 10 registration attempts from the same client IP within 1 minute | `429` | `too many registration attempts, try again later` — also carries a `Retry-After` header (seconds until the bucket refills) |
