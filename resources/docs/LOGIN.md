[← Back to docs index](../TUTORIAL.md)

# Flow: Login (`POST /api/v1/auth/login`)

Verifies credentials and issues a short-lived access token plus a refresh token. Public endpoint —
no auth required.

---

## 1. Contract

- **Method & path**: `POST /api/v1/auth/login`
- **Auth**: none
- **Rate limit**: none yet — a per-`(email, client_ip)` limit is planned but not implemented
- **Request / response**: JSON

### Request

```json
{
  "email": "learner@example.com",
  "password": "correcthorsebattery"
}
```

### Response — `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZDY5MTA2My1kZmI4LTRiMGEtODEyZC1iMzk3NDRmYmRmOWUiLCJpYXQiOjE3OTAwNjA0MTMsImV4cCI6MTc5MDA2MTAxM30.YgHwm76_qf_jDMs3WyTZQWNqnox0Dl08zp0N5LBuiSx-m4DKq0XPkmJsx-qp08gDp0X1iSY7UwIioQ0g4nRoFQ8pT82kUUTtgynhzJxy1_9OaWb5hAPVj4SNcuGMw1TUGg8XMXV96ZebC-JM8O1Bi73ekHPgs8KFtF4-Ee9EF9Upm1dvd2czwSfrFVUcnyop4KlNZbmGxVKz5NecTRNF0AZJ27Py06mp_TCq3WYzyw7eHk7gHQu7T8SDY7uS-PV2j2GgHPinQDf6V6I2nNPeZ00M7uaZSx6cmgSrV1_mGxcH2R30vBQc8n8QNFf0Fr36OieX35KCyK0SwrmfU6xRsw",
  "refreshToken": "YmpMr0H7YyoudJMmB3vzBHl-MwJU_Anr9V9eE6gymzU"
}
```

`accessToken` is a compact RS256 JWS with header `{"alg":"RS256"}` and payload
`{"sub":"<user id>","iat":<issued-at>,"exp":<issued-at + 600>}` — `sub` is the user's UUID (not
their email, since an email can change and an id can't), and `exp - iat` is always exactly 600
seconds (10 minutes). `refreshToken` is a 32-byte random value, URL-safe base64 encoded; only its
SHA-256 hash is ever persisted (see `REGISTER.md`-adjacent reasoning — same "never store the raw
secret" principle, applied to a bearer token instead of a password).

---

## 2. Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant H as AuthController.login
    participant S as AuthService.login
    participant E as BCryptPasswordEncoder
    participant J as JwtService
    participant R as UserRepository / RefreshTokenRepository
    participant DB as PostgreSQL

    Client->>H: POST /api/v1/auth/login
    H->>S: login(request)
    S->>R: findByEmail(email)

    alt email not found, or password doesn't match
        R-->>S: empty / mismatch
        S-->>H: ResponseStatusException(401)
        H-->>Client: 401 "invalid credentials"
    else credentials correct
        R-->>S: User
        S->>J: issueAccessToken(user)
        J-->>S: RS256 JWT (sub=userId, exp=+10min)
        S->>S: generate 32 random bytes -> raw refresh token
        S->>S: SHA-256(raw token) -> hash
        S->>R: save(new RefreshToken(userId, hash, expiresAt, familyId))
        R->>DB: INSERT INTO refresh_tokens (...)
        S-->>H: {accessToken, refreshToken}
        H-->>Client: 200 {accessToken, refreshToken}
    end
```

---

## 3. Step by step

1. **Look up the user and verify the password in one step.**
   `userRepository.findByEmail(email).filter(u -> passwordEncoder.matches(password,
   u.getPasswordHash())).orElseThrow(...)` — "email doesn't exist" and "email exists but password
   is wrong" both fall through to the exact same `orElseThrow`, so both produce the exact same
   `401` body. This isn't a separate enumeration-safety feature bolted on — it's what this code
   shape does automatically, which is also why there's no separate code path to keep in sync if
   one case is ever changed without the other.
2. **Sign the access token.** `JwtService.issueAccessToken` sets `sub` to the user's UUID and signs
   with `RS256` — an *asymmetric* algorithm, meaning anything holding the public key can verify a
   token without ever holding the private signing key. That distinction matters the moment a second
   service (say, `napas-mock`) needs to check "is this request's caller really logged in" without
   `cafefin-api` having to hand it a shared secret.
3. **Generate the refresh token — never store it raw.** 32 bytes from `SecureRandom`, base64-encoded
   for transport. Only its SHA-256 hash goes into `refresh_tokens.token_hash`. If the database ever
   leaked, an attacker gets hashes, not usable tokens — the same reasoning as never storing a
   plaintext password, just with a fast hash (SHA-256) instead of a slow one (BCrypt), because a
   refresh token is already high-entropy and random — there's no dictionary-attack risk a slow hash
   would defend against, so paying BCrypt's deliberate slowness here would only be waste.
4. **Persist the refresh token with a fresh `family_id`.** Every login starts a new rotation family
   (`UUID.randomUUID()`). A later `/refresh` call rotates within that same family; if a
   already-used (revoked) token from that family is ever replayed, the whole family gets revoked at
   once — that's task 1.8.3's breach detection, not built yet, but the `family_id` column exists
   because of it.
5. **Respond with both tokens.** The client stores the access token for `Authorization: Bearer
   <token>` on subsequent requests (see the JWT filter, which protects every endpoint except
   `/register`, `/login`, and `/actuator/health`) and the refresh token for obtaining a new access
   token later, once `/refresh` exists.

---

## 4. Errors

Bodies below are real captured responses (a `LoginEndpointTest` run), not invented.

| Condition | Status | Body |
|---|:---:|---|
| Email doesn't exist | `401` | `{"detail":"invalid credentials","instance":"/api/v1/auth/login","status":401,"title":"Unauthorized"}` |
| Email exists, password is wrong | `401` | **byte-identical** to the row above — same `detail`, same `instance`, same `status`, same `title` |
