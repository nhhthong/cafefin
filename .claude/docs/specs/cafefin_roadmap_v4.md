# CafeFin --- Engineering & Learning Roadmap v3

**Primary Objective**: Build a production-oriented, highly reliable
Java/Fintech platform simulation tailored for deep conceptual mastery
rather than rapid feature output. The target scope now includes the
financial core, external payment flows, event-driven architecture,
observability, frontend, compliance, regulatory reporting, back-office
operations, HA/DR, multi-region resilience, and selected real/sandbox
bank/card integrations.\
**Core Principle**: Strict capability-by-capability progression.
Parallel *architectural layers* are prohibited, but frontend and backend
work for the **same vertical slice** is explicitly allowed and
encouraged. Every architectural decision, lock strategy, transaction
boundary, state transition, and operational control must be fully
understood, tested, and explainable before advancing.

**Technology Baseline** *(validated September 2026 --- re-verify at
project start)*: - **JDK**: 21 LTS or 25 LTS (current LTS). Pin the
choice in the parent `pom.xml` via `<maven.compiler.release>`. -
**Spring Boot**: Pin a specific supported 4.1.x release at project start
(currently target `4.1.1`; re-verify the exact latest patch release
before implementation). Do not keep `3.5.x` as an active baseline.
Re-verify every property and starter referenced in this document
(`spring.mvc.problemdetails.enabled`, structured-logging keys,
Testcontainers integration, Security configuration) against the selected
Boot 4.1.x patch release. - **PostgreSQL**: 16 or 17. - **Kafka**: 4.x
--- KRaft is the only mode; ZooKeeper support is removed entirely. -
**Log collection**: Grafana Alloy (Promtail reached EOL on March 2,
2026).

**Scope Boundary**: KYC/AML, regulatory reporting, HA/DR, multi-region
resilience, back-office operations, and real/sandbox bank/card
integrations are **staged engineering/learning capabilities**. They are
implemented only to the documented demo/simulation scope. CafeFin must
not be represented as a licensed payment service, credit institution,
PCI-certified system, production bank connection, or
regulatory-compliant financial service. Any real-money deployment would
require a jurisdiction-specific legal entity, regulatory
analysis/licensing where applicable, contracted providers, security
assessment/certification requirements, financial-liability/safeguarding
controls, privacy/data-governance controls, and independently validated
operational procedures.

------------------------------------------------------------------------

## Standard Repository & Package Structure

To support multiple independent services (`cafefin-api`,
`cafefin-napas-mock`, and `cafefin-notification` in Layer 3), the
project employs a **Maven multi-module** structure. Each service resides
in its own module, alongside a shared `cafefin-common` module containing
reusable DTOs, domain exceptions, and utility classes.

    cafefin/                           (Parent pom, packaging=pom)
    ├── pom.xml
    ├── cafefin-common/                (Shared DTOs, exceptions, utilities)
    │   └── src/main/java/com/cafefin/common/
    ├── cafefin-api/                   (Primary Spring Boot app — serves API + React SPA)
    │   └── src/main/java/com/cafefin/api/
    ├── cafefin-napas-mock/            (Third-party mock service — Layer 2)
    ├── cafefin-notification/          (Event-driven notification consumer — Layer 3)
    ├── frontend/
    │   └── cafefin-web/               (React + TypeScript + Vite source)
    ├── docs/
    │   ├── adr/
    │   ├── architecture/
    │   └── api/
    └── docker-compose.yml

Within **each individual module** (e.g., `cafefin-api`), packages are
structured **by domain/feature** rather than strictly by technical
layer. Because a fintech service handles diverse functional domains
(e.g., authentication, account management, ledger accounting, payment
integrations), package-by-feature keeps cohesive domain logic closely
coupled. This aligns with industry best practices for microservices and
evolving domain-driven systems.

    com.cafefin.api/
    ├── CafefinApiApplication.java
    ├── config/                        (SecurityConfig, JacksonConfig, KafkaConfig, etc.)
    ├── common/                        (Global exception handlers, base responses, constants)
    │   └── exception/
    ├── auth/                          (Layer 1: Identity & Authentication)
    │   ├── AuthController.java
    │   ├── AuthService.java
    │   ├── dto/
    │   └── entity/                    (User, RefreshToken)
    ├── account/                       (Layer 1: Account Management)
    │   ├── AccountController.java
    │   ├── AccountService.java
    │   ├── dto/
    │   └── entity/                    (Account)
    ├── ledger/                        (Layer 1: Double-Entry Ledger Engine)
    │   ├── LedgerService.java
    │   ├── TransferController.java
    │   ├── TransferService.java
    │   ├── dto/
    │   └── entity/                    (LedgerEntry, Transaction)
    ├── napas/                         (Layer 2: External Payment Gateway Integration)
    │   ├── NapasClient.java           (Outbound HTTP Client to napas-mock)
    │   ├── NapasWebhookController.java
    │   ├── dto/
    │   └── entity/                    (NapasTransaction, NapasWebhookLog)
    ├── notification/                  (Layer 1: Notification abstraction → Layer 3: Kafka Consumer)
    │   ├── NotificationService.java
    │   ├── provider/                  (NotificationProvider, MailpitEmailProvider)
    │   └── entity/                    (NotificationLog)
    ├── compliance/                    (Layer 5: KYC / AML / Regulatory)
    ├── backoffice/                    (Layer 5: Operator workflows & audit)
    └── reliability/                   (Layers 6–7: HA/DR & multi-region concerns)

> **Module Evolution Note**: In Layer 1, notification dispatch lives
> inside `cafefin-api` (`notification/` package, synchronous/`@Async`).
> Layer 3 **extracts** delivery into the standalone
> `cafefin-notification` consumer. After extraction, `cafefin-api`
> retains only outbox event publication; `MailpitEmailProvider` and
> delivery logic move to the consumer module.

    src/test/java/com/cafefin/api/...   (Mirrors domain-based main package structure)
    src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    └── db/migration/                  (Flyway Migrations: V1__..., V2__...)

### Frontend Architecture & Vertical-Slice Rule

The frontend is **not deferred to a final layer**. It starts in Task 0
and evolves alongside the backend through vertical slices. The frontend
source remains a separate Node/TypeScript project, but the
production/demo deployment uses a **single same-origin monolith** for
the core application: Spring Boot `cafefin-api` serves the built React
SPA and `/api/v1/**` from the same application artifact.

**Frontend baseline**: - React 19.x - TypeScript with `strict: true` -
Vite - React Router - TanStack Query for server state - Axios for HTTP
and auth interceptors - OpenAPI + Orval for generated typed API
clients - React Hook Form + Zod for forms and validation - shadcn/ui +
Tailwind CSS for UI components/styling - Vitest + React Testing
Library + MSW for component/unit tests - Playwright for end-to-end
workflows - Node.js 22+ for frontend tooling

**Deployment model**:

    frontend/cafefin-web
            │ npm run build
            ▼
         dist/
            │ copy during application build
            ▼
    cafefin-api/src/main/resources/static/
            │
            ├── React SPA
            └── /api/v1/** REST API

During development, the Vite dev server may run independently with an
`/api` proxy to Spring Boot. The deployment/demo artifact remains the
same-origin Spring Boot application. `Next.js` is intentionally not
introduced because CafeFin is an authenticated application/API product
and does not require a separate SSR application server.

**Vertical-slice rule**: every major backend capability should have its
matching UI surface before the capability is considered demo-complete.
Example: Auth API + Auth UI, Account API + Account UI, Transfer API +
Transfer UI, NAPAS state machine + payment status UI, and later
KYC/AML + back-office consoles.

**Notification rule**: Mailpit is the default **development/demo email
sink**. `NotificationProvider` remains the abstraction boundary so a
production provider can replace `MailpitEmailProvider` later without
changing domain logic.

### Frontend Coverage Rule

Every backend task must explicitly answer four frontend questions before
the task is considered complete:

1.  **Surface** --- Which customer/operator/developer screen changes?
2.  **State** --- Which loading, success, failure, retry, and
    indeterminate states are represented?
3.  **Security** --- What must never be exposed to the browser/user?
4.  **Verification** --- Which unit/component/Playwright test proves the
    UI behavior?

No frontend requirement means "no customer-facing UI is appropriate"; in
that case the task must still document whether an operator/developer
diagnostic surface is useful.

### Global Architectural Rules

1.  **No Default Package**: Explicit domain packages are mandatory.
2.  **DTO / Entity Isolation**: JPA Entities must never be exposed
    directly via HTTP API contracts.
3.  **Encapsulated Domain Modules**: Each domain package contains its
    own local entities and DTOs; global wildcard `model` packages are
    forbidden.
4.  **Centralized Exception Handling**: Global error mapping is handled
    strictly via `@ControllerAdvice` in `common/exception`.

------------------------------------------------------------------------

## Task 0 --- Environment Setup & Infrastructure Baseline

**Objective**: Configure local infrastructure and application
foundations completely so execution across all roadmap capabilities
proceeds without environment setup pauses.

### Task 0.1 --- Core Tools & Runtimes

**Frontend sub-tasks** - \[ \] Add a documented frontend Node.js version
check and `.nvmrc`/Corepack strategy. - \[ \] Verify `node`, package
manager, and browser prerequisites through the project setup
documentation. - \[ \] Sub-task: Install target JDK per Technology
Baseline (JDK 21 or 25 LTS) --- verify via `java -version`. Pin the
version via `.sdkmanrc` or Maven Toolchains so all contributors build
identically. - \[ \] Sub-task: Install Apache Maven --- verify via
`mvn -version`. - \[ \] Sub-task: Install Docker Engine & Docker Compose
--- verify via `docker --version` and `docker compose version`. - \[ \]
Sub-task: Configure Git identity (`user.name`, `user.email`). - \[ \]
Sub-task: Set up IDE (IntelliJ IDEA recommended) with necessary plugin
support (e.g., Lombok annotation processing).

### Task 0.2 --- Repository Initialization

**Frontend sub-tasks** - \[ \] Add frontend CI as explicit jobs: install
→ typecheck → lint → unit/component tests → production build. - \[ \]
Make the frontend build consume a deterministic OpenAPI artifact; do not
make CI depend on a developer machine running Spring Boot. - \[ \] Add
Playwright install/browser caching and a minimal smoke test in CI. - \[
\] Sub-task: Initialize local repository `cafefin`, configure remote
GitHub repository, push initial baseline commit. - \[ \] Sub-task:
Configure standard `.gitignore` for Maven and IDE artifacts (`target/`,
`.idea/`, `*.iml`, `.env`). - \[ \] Sub-task: Configure root parent
`pom.xml` (`packaging=pom`), verify build viability via
`mvn validate`. - \[ \] Sub-task: Create a structured `README.md`
skeleton outlining project architecture. - \[ \] Sub-task: **CI Pipeline
(mandatory for roadmap completion)**: Add a GitHub Actions workflow
running `npm ci && npm run build`, followed by `mvn verify`, on every
push and pull request. Testcontainers runs natively on GitHub-hosted
runners (Docker is available by default). Cache Maven and npm
dependencies between runs. - \[ \] Sub-task: Enable branch protection on
`main`: merges require green CI.

### Task 0.3 --- Local Persistence & Migration Infrastructure

**Frontend sub-tasks** - \[ \] Add `.env.example` entries only for
non-secret frontend build/runtime configuration (API base path,
environment label). Never put JWT/HMAC/database secrets in Vite-exposed
variables. - \[ \] Document same-origin production configuration so the
browser uses `/api/v1` rather than a hard-coded backend host. - \[ \]
Sub-task: Write `docker-compose.yml` declaring a managed PostgreSQL
container (configured with persistent volumes). - \[ \] Sub-task:
Configure environment variable management (`.env` for local execution;
check in `.env.example` as standard template). - \[ \] Sub-task: Verify
local database connectivity via external database GUI client or
`psql`. - \[ \] Sub-task: Integrate Flyway into `cafefin-api`, write
initial baseline migration (`V1__init.sql`), verify auto-execution on
app startup. - \[ \] Sub-task: Enable
`flyway.validateMigrationNaming = true` to catch naming convention
errors during build phases rather than deployment runtime. - \[ \]
Sub-task: **Strict Rule on Immutability**: Applied migrations must
**never** be edited. Flyway validates schema state against
`flyway_schema_history` checksums. Editing applied scripts results in
`CHECKSUM_MISMATCH` and aborts application startup. Fixes must be
executed strictly via incremental migrations (`V<n+1>__...`). -
*Verification*: Mutate an applied migration script locally and verify
that startup fails cleanly with a checksum error, then revert. - \[ \]
Sub-task: Enforce dual database user segregation from inception: -
`migration` user: Schema owner permitted to perform DDL actions. -
`runtime` user: Application user restricted exclusively to DML
operations (`SELECT`, `INSERT`, `UPDATE`, `DELETE`). - Update
`.env.example` to explicitly represent both roles.

### Task 0.4 --- Module Initialization (`cafefin-api`)

**Frontend sub-tasks** - \[ \] Create the application shell, router,
error boundary, loading/failure states, and health/status surface. - \[
\] Verify the SPA can start against the Spring Boot API in the selected
profile. - \[ \] Sub-task: Generate `cafefin-api` baseline with
dependencies: Web, Spring Data JPA, PostgreSQL Driver, Validation,
Actuator. - \[ \] Sub-task: Validate bootstrap via `mvn spring-boot:run`
and verify `GET /actuator/health` returns `{"status":"UP"}`. - \[ \]
Sub-task: Configure profile-based configurations (`application.yml` and
`application-dev.yml`), verifying active profile activation via
`-Dspring.profiles.active=dev`. - \[ \] Sub-task: Establish package
skeleton (`config/`, `common/exception/`).

### Task 0.5 --- Testing Infrastructure

**Frontend sub-tasks** - \[ \] Add MSW handlers for health/auth baseline
APIs. - \[ \] Add component-test infrastructure for forms, API errors,
loading states, and protected routes. - \[ \] Add a Playwright smoke
test: open app → login screen → API health/status visible. - \[ \]
Sub-task: Add dependencies: `spring-boot-starter-test`,
`org.testcontainers:postgresql`, and `spring-boot-testcontainers` (test
scope). - \[ \] Sub-task: Author a foundational integration test
verifying application context load against a managed PostgreSQL
container. - Use **`@ServiceConnection`** (Spring Boot 3.1+) on static
`@Container` declarations. Avoid manual `@DynamicPropertySource`
setup. - Container fields **must be static** to allow container reuse
across the test class lifecycle. Instance-level fields incur massive
performance penalties by starting/stopping containers per test method. -
\[ \] Sub-task: ⚠️ **Do NOT enable JUnit parallel execution**.
Testcontainers documentation explicitly specifies that `@Testcontainers`
extension concurrency support is unverified and prone to race
conditions. - *Note*: Parallel thread invocation *inside* an individual
concurrency test (e.g., using `CompletableFuture` in Task 1.3) is
completely valid and encouraged. - \[ \] Sub-task: Enable local
container reuse (`testcontainers.reuse.enable=true`) to minimize startup
latency during local development iterations. - \[ \] Sub-task: Confirm
suite execution consistency via both IDE and command line (`mvn test`).

### Task 0.6 --- Code Quality & API Standards

**Frontend sub-tasks** - \[ \] Define frontend API/error conventions for
RFC 9457: map `type`, `title`, `status`, `detail`, application error
code, and `traceId` into a reusable error model. - \[ \] Add money
formatting/parsing utilities that treat API monetary strings as decimal
values and never use JavaScript floating-point arithmetic for financial
calculations. - \[ \] Add reusable form validation for currency/amount
scale and a consistent API error renderer. - \[ \] Document
accessibility baseline: keyboard navigation, labels, focus states,
semantic controls, and WCAG-oriented checks. - \[ \] Sub-task: Integrate
static code analysis or formatting plugins (e.g., Spotless or
Checkstyle) bound to Maven build phases. - \[ \] Sub-task: Finalize API
conventions: URIs prefixed with `/api/v1`, standard pagination
parameters (`page`, `size`), plural noun resource identifiers, and all
authenticated/public webhooks routed under `/api/v1/webhooks/**`. - \[
\] Sub-task: Standardize error payload formatting strictly to **RFC
9457** (`application/problem+json`). - Utilize Spring Framework 6+
`ProblemDetail` abstractions directly. Do not build proprietary wrapper
classes. - Enable `spring.mvc.problemdetails.enabled: true`. - Customize
standard exceptions via
`@ControllerAdvice extends ResponseEntityExceptionHandler`. - Extend
`ProblemDetail` fields via `.setProperty()` (e.g., adding application
error codes, `traceId`). - *Verification*: Trigger a validation failure
and confirm `Content-Type: application/problem+json` containing standard
RFC 9457 fields (`type`, `title`, `status`, `detail`). - \[ \] Sub-task:
Standardize Monetary Precision Rules: - **Never use floating-point types
(`double`, `float`)**. - Persist values in database storage as **integer
minor units** (`long` / `BIGINT`). Scale MUST follow **ISO 4217**
decimal digits: - `$12.34 USD` → `1234` (USD minor unit scale: 2) -
`12 VND` → `12` (VND minor unit scale: **0**) - `12.345 BHD` → `12345`
(BHD minor unit scale: 3) - Perform intermediate domain arithmetic using
`BigDecimal` initialized strictly via `String` constructors
(`new BigDecimal("0.1")`), avoiding `double` constructors. - Evaluate
equality using `compareTo() == 0`, avoiding `.equals()` due to scale
variance sensitivity. - **JSON Serialization**: Serialize monetary
values as JSON **strings**, not numbers. Browsers and client JS engines
parse numbers as IEEE-754 double precision floats, reintroducing
precision loss. - Annotate `BigDecimal` fields with
`@JsonFormat(shape = JsonFormat.Shape.STRING)`. - Enforce typed DTO
mappings for deserialization. Do not pass monetary amounts through
untyped maps (`Map<String, Object>`). - Domain Encapsulation: Represent
currency and scale using a domain `Money` value object
(`long amountInMinorUnit`, `Currency currency`). - *Verification*: Test
JSON serialization round-trips ensuring scale retention (e.g., `"1.10"`
stays `"1.10"`). Verify raw API JSON outputs serialize numbers enclosed
in quotes (`"12.34"`). - \[ \] Sub-task: Integrate `springdoc-openapi`
--- expose `/v3/api-docs` and Swagger UI. Generated, always-current API
contracts are the primary inter-team communication artifact in
professional fintech environments. - \[ \] Sub-task: Define repository
branch and commit message naming standards. - \[ \] Sub-task: Create
directory `docs/adr/` for documenting Architectural Decision Records
(ADRs). - *Initial ADR*: Rationale for choosing Pessimistic Locking over
Optimistic Locking for monetary debit pathways.

### Task 0.7 --- Java Language Acclimatization (Developers New to Java)

**Frontend sub-tasks** - \[ \] No production UI feature is required; use
the primer to understand the TypeScript equivalents of
records/immutability, generics, async/concurrency, and numeric
precision. - \[ \] Document where TypeScript domain types are generated
versus manually authored.

This roadmap teaches Java *through* implementation rather than in
isolation. Before Layer 1, complete a focused pass over modern Java
features and map each to where it first appears in this project:

  -----------------------------------------------------------------------
  Language Feature                    First Applied In
  ----------------------------------- -----------------------------------
  Records (immutable DTOs)            All `dto/` packages (Task 1.1+)

  Sealed interfaces +                 Domain state modeling (Task 1.3)
  pattern-matching `switch`           

  Generics & bounded types            Repository abstractions, `Money`
                                      value object (Task 0.6)

  Streams & Collectors                Balance aggregation, reconciliation
                                      (Tasks 1.2, 1.7)

  `Optional` discipline               Repository return contracts (Task
                                      1.1+)

  `CompletableFuture` /               Concurrency tests (Task 1.3)
  `ExecutorService`                   

  `BigDecimal` semantics              Monetary precision rules (Task 0.6)
  -----------------------------------------------------------------------

-   [ ] Sub-task: Complete one modern-Java primer (official tutorials on
    records, sealed classes, streams, `Optional`); no project code
    required.
-   [ ] Sub-task: **Virtual Threads Decision (ADR)**: Evaluate
    `spring.threads.virtual.enabled=true` (JDK 21+). Document the
    interaction between virtual threads, JDBC connection consumption,
    and pessimistic lock hold times --- and why this project enables or
    defers them. Note: `synchronized`-block carrier-thread pinning is
    resolved as of JDK 24 (JEP 491); earlier JDKs pin.

------------------------------------------------------------------------

### Task 0.8 --- Frontend Foundation (Starts Immediately)

**Frontend sub-tasks** - \[ \] Complete login route, authenticated
shell, navigation, global error boundary, health indicator, responsive
layout, and route-level loading states. - \[ \] Configure TanStack Query
defaults, Axios interceptors, auth token handling, and
logout/session-expiry behavior. - \[ \] Generate Orval clients
reproducibly and forbid manual edits to generated files. - \[ \] Add
Playwright coverage for login and direct refresh of nested SPA routes. -
\[ \] Add a reusable UI kit: buttons, inputs, dialogs, tables, badges,
toasts, loading/skeleton states, empty states, and confirmation dialogs.

-   [ ] Sub-task: Initialize `frontend/cafefin-web` with React +
    TypeScript + Vite.
-   [ ] Sub-task: Configure strict TypeScript, ESLint/formatting, path
    aliases, environment handling, and npm scripts.
-   [ ] Sub-task: Add React Router, TanStack Query, Axios, React Hook
    Form, Zod, shadcn/ui, and Tailwind CSS.
-   [ ] Sub-task: Add Vitest, React Testing Library, MSW, and
    Playwright.
-   [ ] Sub-task: Configure Vite development proxy from `/api` to
    `cafefin-api`.
-   [ ] Sub-task: Integrate springdoc OpenAPI output with Orval and
    generate typed TypeScript clients/hooks. Generated code must be
    reproducible and excluded from manual editing.
-   [ ] Sub-task: Configure production packaging with a multi-stage
    Docker build (Node build stage → Spring Boot runtime image) so the
    React `dist/` assets are packaged into `cafefin-api` without
    committing generated assets to source control.
-   [ ] Sub-task: Configure SPA history fallback for non-API routes so
    browser refreshes on routes such as `/dashboard` and `/backoffice`
    resolve to the React entry point while `/api/**` remains handled by
    Spring MVC.
-   [ ] Sub-task: Implement the first shell: login route, authenticated
    application shell, navigation, global error boundary, RFC 9457 error
    rendering, and health/status indicator.
-   [ ] Verification: `docker compose up --build` exposes the frontend
    and REST API from the same origin; browser E2E test can log in
    against the local application and direct-refresh a nested SPA route.

## Layer 1 --- Core Java & Spring Engine

**Core Learning Objectives**: Transaction boundaries (`@Transactional`),
database isolation levels, pessimistic vs. optimistic locking
strategies, lock timeouts, API idempotency guarantees, state machine
design, concurrency testing, database-level invariant enforcement, and
connection-pool dynamics under lock contention.

**Self-Assessment Gate**: 1. If two concurrent transfer requests execute
against the same account simultaneously, can a race condition occur?
Prove non-existence via test. 2. If the application crashes *after* a
transfer transaction commits but *before* the idempotency response is
persisted, what occurs upon client retry? Prove behavior via test.

### Task 1.1 --- Identity & Authentication

**Frontend sub-tasks** - \[ \] Build registration, login,
refresh/session bootstrap, logout, validation, and protected-route
flows. - \[ \] Handle `401` consistently and redirect to login without
creating redirect loops. - \[ \] Do not persist long-lived bearer
credentials in unsafe browser storage; document the selected
token/session strategy and its threat model. - \[ \] Add Playwright
tests for duplicate registration, invalid login, successful login,
refresh, logout, and expired-session handling.

**UI slice**: Login, registration, session refresh, and authenticated
route protection are implemented in the React application as part of
this task. - \[ \] Sub-task: Model `User` entity (`id`, `email`,
`passwordHash`, `createdAt`). - \[ \] Sub-task: Implement
`POST /api/v1/auth/register` --- hash passwords using BCrypt; validate
unique email constraint. - *Verification*: Register duplicate email -\>
expect `409 Conflict`. - \[ \] Sub-task: Implement
`POST /api/v1/auth/login` --- verify password credentials, issue signed
JWT access tokens. - *Verification*: Invalid credentials -\>
`401 Unauthorized`; valid credentials -\> parseable JWT with accurate
claims. - \[ \] Sub-task: **JWT Signing Algorithm Decision (ADR)**:
Select asymmetric signing (RS256 or EdDSA) over HS256. With multiple
services (Layer 2+), asymmetric keys allow downstream services to verify
tokens using the public key without holding the signing secret. Define
access-token TTL (short: 5--15 minutes) and clock-skew tolerance
explicitly. - \[ \] Sub-task: Model `RefreshToken` entity (`id`,
`userId`, `tokenHash`, `expiresAt`, `revoked`, `familyId`). - **Never
persist raw refresh tokens.** They are bearer credentials: store a
SHA-256 hash and compare on lookup, so a database leak does not equal
session theft. BCrypt is unnecessary here --- tokens are high-entropy
random values, not low-entropy passwords. - \[ \] Sub-task: Implement
`POST /api/v1/auth/refresh` --- validate refresh tokens, perform token
rotation (revoke consumed token, issue new token pair). - \[ \]
Sub-task: Implement JWT filter --- intercept and authorize secure
endpoints.

### Task 1.2 --- Account Engine & Derived Balances

**Frontend sub-tasks** - \[ \] Build account dashboard, account
list/detail, balance display, and transaction-history screens. - \[ \]
Implement keyset-pagination UX: cursor-based next/previous loading
rather than offset pagination assumptions. - \[ \] Display
currency-aware amounts and transaction direction/status without doing
client-side financial calculations from binary floating-point numbers. -
\[ \] Prevent users from selecting SYSTEM accounts in normal
account-selection UI.

**UI slice**: Dashboard/account overview and transaction-history views
consume the typed OpenAPI client.

**Core Architectural Decision**: Account balance is **NOT** stored as a
mutable column on the `Account` table. Balance is a **derived value**:
$$\text{Balance} = \sum \text{CREDIT} - \sum \text{DEBIT}$$ computed
from immutable entries in the `LedgerEntry` table.

*Rationale*: Storing mutable balance columns creates state
synchronization drift between historical transaction entries and cached
balance aggregates. Double-entry accounting explicitly exists to prevent
this class of defect.

**Fundamental Invariant of Double-Entry Accounting**: Every debit must
have an equal and opposite credit. Money is neither created nor
destroyed; the algebraic sum of debits and credits across the entire
system must remain zero at all times.

**Sign Convention (explicit --- not universal)**:
`Balance = ΣCREDIT − ΣDEBIT` holds only for **credit-normal** accounts.
This project fixes the convention as follows: user accounts and
`NAPAS_CLEARING` are credit-normal (deposits are credits);
`NAPAS_SETTLEMENT` (introduced in Task 2.2) absorbs value that exits the
system. Debit-normal vs. credit-normal account classes (assets
vs. liabilities) are covered in the cited TigerBeetle documentation.
Every ledger flow in Layer 2 must be re-derived against this convention
before implementation --- sign errors concentrate precisely in
clearing/settlement flows.

-   [ ] Sub-task: Model `Account` entity (`id`, `userId`, `currency`,
    `accountType` \[USER/SYSTEM\], `createdAt`).
-   [ ] Sub-task: Implement System Accounts --- internal ledger accounts
    (seed `NAPAS_CLEARING` and `NAPAS_SETTLEMENT` via Flyway migration;
    their roles are defined in Task 2.2).
    -   *Verification*: Restrict standard users from initiating direct
        public transfers to or from SYSTEM accounts.
-   [ ] Sub-task: Implement `POST /api/v1/accounts` --- provision user
    accounts.
-   [ ] Sub-task: Model `LedgerEntry` entity (`id`, `accountId`,
    `transactionId`, `type` \[DEBIT/CREDIT\], `amount`, `createdAt`).
    Immutable append-only model.
    -   Index `accountId` to optimize aggregate sum computations.
-   [ ] Sub-task: Implement `GET /api/v1/accounts/{id}/balance` ---
    compute real-time balance via `SUM(CREDIT) - SUM(DEBIT)`.
    -   *Verification*: Validate correct balance calculation following
        multiple transfer operations. Verify accounts without entries
        return `0` without throwing null errors.
-   [ ] Sub-task: Implement `GET /api/v1/accounts/{id}/transactions` ---
    paginated transaction history with time-range filtering. Use
    **keyset (seek) pagination**
    (`WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?`)
    rather than `OFFSET`: offset pagination degrades linearly on large
    ledgers and yields row skew under concurrent inserts.

### Task 1.2c --- Deterministic Demo Data & Ledger Seeding

**Frontend sub-tasks** - \[ \] Provide documented demo-user entry points
or a development-only demo selector. - \[ \] Show seeded opening
balances in the dashboard so the first transfer can be demonstrated
immediately. - \[ \] Never expose seed/SYSTEM-account mutation controls
to normal users.

-   [ ] Sub-task: Create deterministic demo users and accounts for
    local/demo profiles only.
-   [ ] Sub-task: Seed opening balances through explicit double-entry
    ledger transactions from a SYSTEM funding account; never mutate an
    `Account.balance` field because balance is derived.
-   [ ] Sub-task: Make demo seeding idempotent and safe to rerun.
-   [ ] Sub-task: Ensure demo seed data is disabled or rejected in
    production profiles.
-   [ ] Frontend: Add a demo-data bootstrap/status indicator or
    documented demo login flow; never expose privileged SYSTEM-account
    controls to normal users.
-   [ ] Verification: Fresh `docker compose up` produces the documented
    demo accounts and non-zero balances without manual SQL.

### Task 1.2b --- Denormalized Running Balance (`balance_after`)

**Frontend sub-tasks** - \[ \] Show the same balance returned by the
optimized endpoint; the frontend must not calculate authoritative
balances from transaction history. - \[ \] Add a development-only
diagnostic view/test that compares displayed balance with the backend's
invariant endpoint when available. - \[ \] Sub-task: Add `balanceAfter`
and a per-account monotonic `entrySequence` column to `LedgerEntry` ---
both computed once at insertion, immutable thereafter. - **"Latest
entry" must be defined by `entrySequence`, never by global `id` or
timestamp**: sequence-generated IDs can commit out of order, and
timestamps tie. Per-account `FOR UPDATE` serialization (Task 1.3) makes
sequence assignment safe. Enforce
`UNIQUE (account_id, entry_sequence)`. - \[ \] Sub-task: Optimize
`GET /api/v1/accounts/{id}/balance` to fetch `balanceAfter` of the
latest ledger entry ($O(1)$ lookup time complexity). - *Verification*:
Assert `balanceAfter` on the latest entry strictly matches full `SUM()`
calculation across all entries. Verify sequence continuity
post-concurrent operations.

### Task 1.3 --- Internal Double-Entry Transfers & Concurrency

**Frontend sub-tasks** - \[ \] Build transfer form with source account,
destination account, amount, currency, validation, confirmation, submit
state, and generated idempotency-key visibility. - \[ \] Disable
duplicate submission while a request is in flight but still handle
browser/network retries safely. - \[ \] Display transfer lifecycle and
resulting transaction detail. - \[ \] Add UI for `409`, fingerprint
conflict, insufficient funds, lock timeout, quota exceeded, and RFC 9457
errors. - \[ \] Add Playwright tests for normal transfer, duplicate
click/retry, invalid amount, insufficient funds, and final
balance/history.

**UI slice**: Transfer form, generated idempotency key display,
validation errors, duplicate-request outcome, transaction status, and
resulting balance changes.

**Core Architectural Decision**: Enforce serialization using
**pessimistic row-level locking** (`SELECT ... FOR UPDATE`) on relevant
`Account` rows, acquiring locks strictly sorted by `accountId` in
ascending order.

*Locking Rationale*: The `Account` row serves as a **lock token**.
Acquiring a pessimistic lock on the account row serializes concurrent
ledger write operations for that account.

Optimistic locking remains technically possible (e.g., using Hibernate's
`OPTIMISTIC_FORCE_INCREMENT`), but in high-concurrency environments
targeting hot accounts, optimistic locking induces severe retry storms,
unbounded tail latency, and high transaction abort rates. Pessimistic
locking provides deterministic, fail-fast boundary enforcement.

Trade-offs: - Deadlock Risk: Mitigated by strictly sorting account locks
by ID. - Throughput Bottleneck: Throughput per single account is bound
by lock holding time. Keep transactions short; never make outbound HTTP
calls or email transmissions inside transaction boundaries. - Unbounded
Blocking: Solved by specifying lock timeouts.

#### Implementation Tasks & Idempotency Lifecycle

-   [ ] Sub-task: Model `IdempotencyKey` entity scoped by authenticated
    user (`userId`, `key`, `requestFingerprint`, `state`
    \[IN_FLIGHT/COMPLETED\], `responseBody`, `responseStatus`,
    `createdAt`, `expiresAt`, `leaseExpiresAt`). Enforce
    `PRIMARY KEY (user_id, key)` or equivalent unique constraint. Never
    allow one user's idempotency replay to resolve another user's
    response.
-   [ ] Sub-task: Model `Transaction` entity (`id`, `userId`, `status`
    \[PENDING/COMPLETED/FAILED\], `idempotencyKey`, `fromAccountId`,
    `toAccountId`, `amount`, `createdAt`). Enforce uniqueness using the
    same user-scoped idempotency namespace.
-   [ ] Sub-task: **Request Validation Gate** (evaluated before any
    idempotency claim): reject `fromAccountId == toAccountId`, reject
    transfers between accounts of differing currencies, reject
    non-positive amounts, and reject scale exceeding the currency's ISO
    4217 minor units.
-   [ ] Sub-task: Implement `POST /api/v1/transfers` requiring HTTP
    header `Idempotency-Key`.
    -   **Atomic Key Reservation**: Execute atomic insertion
        `INSERT ... ON CONFLICT DO NOTHING` setting state to
        `IN_FLIGHT`. Do not separate existence checks and insertions
        into two uncommitted SELECT/INSERT statements.
    -   If insertion fails to claim the row:
        -   State `COMPLETED` -\> Replay stored response payload
            directly without re-executing business logic.
        -   State `IN_FLIGHT` -\> Return `409 Conflict`.
    -   Fingerprint Mismatch -\> If key exists with a different request
        body fingerprint, return `422 Unprocessable Entity`.
        *(Deliberate deviation: Stripe returns `400` with error type
        `idempotency_error`; record the deviation and rationale in the
        ADR.)*
    -   Business Execution Completion -\> Update state to `COMPLETED`,
        storing response payload and HTTP status code.

    **Transaction Boundaries for Idempotency Claims**: Claiming an
    idempotency key and executing a transfer **must not** share the same
    database transaction. If combined, a business rollback destroys the
    claim record, allowing subsequent retries to re-execute logic
    non-idempotently.
    -   Structure the flow as **three sequential top-level
        transactions** driven by a non-transactional orchestrator
        method: (1) claim key `IN_FLIGHT`, (2) execute the business
        transfer, (3) finalize key `COMPLETED`.
    -   ⚠️ **Do NOT implement this via `REQUIRES_NEW` nested inside an
        open transaction.** A suspended outer transaction retains its
        pooled connection while the inner transaction demands a second
        one; under load this exhausts HikariCP and produces an
        application-level deadlock --- a classic production incident
        that never reproduces in low-concurrency local testing.
    -   Document this design choice --- including the connection-pool
        rationale --- in an ADR.

    **Lease Management & Unhandled Failure Recovery**:
    -   Request Validation Failures -\> Release claim immediately
        (delete record or set to `RELEASED`).
    -   Controlled Business Failures (e.g., Insufficient Funds) -\> Mark
        key `COMPLETED` storing the structured error response payload.
        Subsequent retries replay the error response without
        re-evaluating balances.
    -   Application Crash / Timeout -\> Rely on
        `leaseExpiresAt = now() + TTL`. Abandoned `IN_FLIGHT` keys past
        lease expiration may be reclaimed atomically:
        `UPDATE ... WHERE state='IN_FLIGHT' AND leaseExpiresAt < now()`.
    -   **Stale Lease Recovery Guard**: Set a `UNIQUE` database
        constraint on `Transaction.idempotencyKey`. Upon reclaiming an
        expired lease, inspect the database for existing transaction
        records associated with that key prior to re-execution. If a
        transaction exists, build the response from the historical
        record without re-executing balance deductions.
    -   **Retention & Purge**: Schedule a purge job deleting keys past
        `expiresAt` (reference: Stripe prunes after 24 hours). Unbounded
        idempotency tables progressively degrade the hot-path
        `INSERT ... ON CONFLICT`.
-   [ ] Sub-task: Double-Entry Enforcement --- Every transfer must write
    exactly two `LedgerEntry` records (1 DEBIT on sender, 1 CREDIT on
    receiver).
    -   *Verification*: Confirm
        $\sum \text{DEBIT} - \sum \text{CREDIT} = 0$ for every single
        transaction.
-   [ ] Sub-task: Encapsulate transfer logic in `@Transactional`: lock
    accounts -\> verify funds -\> write Transaction record -\> create
    Ledger entries.
    -   *Verification*: Simulate mid-transaction runtime exception -\>
        verify zero ledger records remain (complete atomic rollback).
-   [ ] Sub-task: Apply sorted pessimistic locks using Spring Data JPA
    `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
    -   *Verification*: Execute concurrent transfers from a single
        account using `CompletableFuture`. Confirm final balances match
        mathematical expectations without lost updates.
    -   *Verification (Deadlock Prevention)*: Execute concurrent
        cross-transfers (Thread 1: A-\>B, Thread 2: B-\>A). Confirm all
        transactions resolve without deadlocks.
-   [ ] Sub-task: Configure explicit **Lock Timeouts**.
    -   Apply JPA Query Hint `jakarta.persistence.lock.timeout` (`0` for
        `NOWAIT`).
    -   Configure PostgreSQL session/connection parameter
        `lock_timeout`.
    -   *Verification*: Hold an explicit lock in Thread 1; execute
        transfer in Thread 2 -\> expect Thread 2 to fail fast within
        defined timeout thresholds, mapping cleanly to a domain error
        response.
-   [ ] Sub-task: **Connection Pool Discipline**: Size HikariCP
    explicitly (`maximumPoolSize`, `connectionTimeout`) relative to
    expected lock-hold times and concurrent request ceilings; never rely
    on defaults for a lock-heavy workload.
    -   *Verification*: Saturate transfers against one hot account with
        pool size deliberately reduced (e.g., 2) -\> confirm requests
        fail fast on `connectionTimeout` with a mapped domain error
        rather than hanging indefinitely.
-   [ ] Sub-task: Define overdraft policy by account class. USER
    accounts must not overdraft; SYSTEM accounts require an explicit
    per-account policy because clearing/settlement flows may
    legitimately cross zero. Enforce the policy after pessimistic lock
    acquisition. Until Task 1.2b lands this is an O(n) `SUM()` inside
    the lock window (temporarily acceptable); after 1.2b, read the
    locked account's latest `balanceAfter`.

### Task 1.3b --- Isolation Level Analysis

**Frontend sub-tasks** - \[ \] No special customer UI is required; add a
developer/demo diagnostics page or test artifact showing
isolation/concurrency test results and lock-timeout behavior. - \[ \]
Sub-task: Explicitly query runtime transaction isolation level
(`SHOW transaction_isolation`). - \[ \] Sub-task: Write a demonstration
test omitting `FOR UPDATE` under `READ COMMITTED` to observe lost
updates under concurrent access. Tag with `@Tag("demo")`. - \[ \]
Sub-task: Test identical lockless scenario under `REPEATABLE READ`
isolation level to observe SQLSTATE `40001` serialization failures. - \[
\] Sub-task: Document ADR detailing why `READ COMMITTED` paired with
explicit row-level locking is selected over elevated global isolation
levels.

### Task 1.3c --- Contention Benchmarking (Evidence over Assertion)

**Frontend sub-tasks** - \[ \] Add an optional developer-only
performance view that displays recorded P50/P99/throughput results from
the benchmark artifact; do not run load tests from the browser.

The pessimistic-vs-optimistic rationale in Task 1.3 must be **measured,
not asserted**. - \[ \] Sub-task: Script a load test (k6 or Gatling)
hammering concurrent transfers against a single hot account. - \[ \]
Sub-task: Run the identical load against a throwaway branch implementing
`OPTIMISTIC_FORCE_INCREMENT` with bounded retries. - \[ \] Sub-task:
Record P50/P99 latency, abort/retry rates, and throughput for both
strategies; attach the results to the locking ADR.

### Task 1.4 --- Daily Quota Rate Limiting

**Frontend sub-tasks** - \[ \] Show remaining daily quota and used quota
on the transfer screen when the API exposes it. - \[ \] Map quota
rejection to a clear RFC 9457 message and disable/annotate the transfer
action when the limit is known to be exhausted. - \[ \] Do not trust
client-side quota checks as enforcement. - \[ \] Sub-task: Model
`DailyQuotaUsage` entity (`accountId`, `date`, `usedAmount`) with a
compound unique index `(accountId, date)`. - \[ \] Sub-task: Evaluate
and increment quota usage within the primary transfer transaction
*after* acquiring account locks. - *Verification*: Concurrently submit
transfers exceeding daily limits -\> assert strict limit adherence.

### Task 1.5 --- Transaction Disputes & Refunds

**Frontend sub-tasks** - \[ \] Transaction detail shows refund
eligibility, authorization state, full/partial refund form, remaining
refundable amount, confirmation, idempotency-aware retry, and resulting
refund transaction. - \[ \] Make irreversible/high-impact refund actions
explicit and auditable in the UI.

**UI slice**: Transaction detail and refund workflow with explicit
confirmation and immutable original-transaction display. - \[ \]
Sub-task: Implement `POST /api/v1/transfers/{id}/refund` requiring
`Idempotency-Key`, creating an inverse transfer referencing
`originalTransactionId`. - Define explicit refund authorization
(initiator/recipient/operator roles) and enforce it server-side. -
Define full vs. partial refund semantics and enforce
`refundedAmount <= originalAmount`. - Never mutate or delete original
transaction records or ledger entries. - Enforce refund
uniqueness/idempotency so a timed-out client retry cannot create a
second refund. - *Verification*: Validate duplicate refunds are
rejected/replayed correctly; reject refunds on non-COMPLETED
transactions; reject unauthorized refunds; reject cumulative refunds
above the original amount. - Frontend: Transaction detail shows refund
eligibility, authorized action, full/partial refund form, remaining
refundable amount, confirmation step, idempotency-aware retry state, and
final refund transaction link.

### Task 1.6 --- Synchronous / Asynchronous Notifications

**Frontend sub-tasks** - \[ \] Show notification preference/status only
if the backend exposes it; never let notification failure block transfer
UI success. - \[ \] Add a developer/demo notification inspection link or
documented Mailpit workflow without exposing production provider
credentials. - \[ \] Sub-task: Add Mailpit service to
`docker-compose.yml`. - \[ \] Sub-task: Implement `NotificationProvider`
interface backed by `MailpitEmailProvider`. - \[ \] Sub-task: Dispatch
notifications post-commit using `@Async` or Spring
`TransactionalEventListener(phase = AFTER_COMMIT)`. External
notification failures must never roll back completed transfers. - \[ \]
Sub-task: Model `NotificationLog` entity to track dispatch history.

### Task 1.7 --- Internal Reconciliation Job

**Frontend sub-tasks** - \[ \] Add a developer/operator reconciliation
dashboard showing latest run, invariant status, discrepancy count, and
traceable result IDs. - \[ \] Do not expose silent auto-remediation
controls from the normal customer UI. - \[ \] Sub-task: Implement a
scheduled reconciliation job validating core invariants: -
$\sum \text{LedgerEntries} = 0$ across all accounts (including SYSTEM
accounts: `NAPAS_CLEARING`, `NAPAS_SETTLEMENT`). - Every `COMPLETED`
transaction contains balanced, matching entries. - Latest `balanceAfter`
matches computed historical sum. - \[ \] Sub-task: Record audit results
in `ReconciliationResult` table without silent auto-remediation. -
*Verification*: Inject a deliberately unbalanced entry via a superuser
connection (bypassing triggers) -\> confirm the job detects and reports
the imbalance. A reconciliation job proven only to run --- never to
catch --- proves nothing.

### Task 1.8 --- Production Hardening & Security Safeguards

**Frontend sub-tasks** - \[ \] Add consistent `429` UI with
`Retry-After` messaging and disabled/retry behavior. - \[ \] Ensure
authentication forms do not reveal whether an email exists. - \[ \]
Verify logs/telemetry never receive raw tokens, passwords, secrets, or
unmasked sensitive identifiers from frontend error reporting. - \[ \]
Add security-focused browser tests for route protection and accidental
secret exposure in rendered state.

#### 1.8.1 --- Database-Level Immutability Enforcement

-   [ ] Sub-task: Block `UPDATE` and `DELETE` operations on
    `ledger_entries` at the database level:
    -   Execute SQL privilege revocation:
        `REVOKE UPDATE, DELETE ON ledger_entries FROM <app_runtime_user>;`.
    -   Define PostgreSQL `BEFORE UPDATE OR DELETE` database triggers
        raising explicit exceptions.
    -   *Verification*: Execute explicit `UPDATE` and `DELETE`
        statements against `ledger_entries` via application runtime
        connection -\> assert database rejection.
-   [ ] Sub-task: Attach trigger mechanisms on `transactions` blocking
    mutations once `status = 'COMPLETED'`.

#### 1.8.2 --- Financial Integrity Database Constraints

-   [ ] Sub-task: Add SQL check constraints: `CHECK (amount > 0)`.
-   [ ] Sub-task: Add compound unique index:
    `UNIQUE (transaction_id, account_id, type)`.
-   [ ] Sub-task: Enforce strict `NOT NULL` and foreign key
    relationships.

#### 1.8.3 --- Refresh Token Breach & Reuse Detection

-   [ ] Sub-task: Add `familyId` tracking to `RefreshToken`.
-   [ ] Sub-task: Detecting consumption of an already-revoked refresh
    token indicates token theft. Instantly **revoke all tokens matching
    that `familyId`**, forcing re-authentication.
    -   *Verification*: Replay a revoked refresh token -\> confirm
        immediate invalidation of all descendant tokens within the
        family.

#### 1.8.4 --- Endpoint Rate Limiting

-   [ ] Sub-task: **Implementation Choice**: Use Bucket4j. In-memory
    buckets are valid **only under this project's single-API-instance
    assumption**; document in an ADR that horizontal scaling requires a
    shared backend (PostgreSQL- or Redis-backed buckets) --- note that
    Layer 3 already assumes multi-instance pollers.
-   [ ] Sub-task: Apply rate limiting on `POST /api/v1/auth/login` by
    key `(email, client_ip)`.
-   [ ] Sub-task: Apply rate limiting on `POST /api/v1/auth/register` by
    `client_ip`.
-   [ ] Sub-task: Return HTTP `429 Too Many Requests` formatted as RFC
    9457 containing `Retry-After` headers.
-   [ ] Sub-task: Standardize error messaging across non-existent emails
    and incorrect passwords to prevent account enumeration vectors.

#### 1.8.5 --- Secrets Management & PII Masking

-   [ ] Sub-task: Read JWT and HMAC secrets exclusively from environment
    variables. Abort application context load if required secrets are
    absent.
-   [ ] Sub-task: Enforce strict logging redaction: account numbers
    masked (e.g., showing last 4 digits only); passcodes, tokens, and
    cryptographic signatures excluded from application logs.
    -   *Verification*: Execute full test flow under `DEBUG` logging
        level -\> grep logs to confirm zero raw credentials, keys, or
        unmasked identifiers exist.

------------------------------------------------------------------------

## Layer 2 --- Service Segregation & External Integrations

**Core Learning Objectives**: Distributed transactions, Saga Pattern
implementation, circuit breaker mechanics, webhook signature
verification, and managing in-doubt integration states.

**Self-Assessment Gate**: 1. If the NAPAS mock responds with a gateway
failure after local user accounts are debited, where are the funds? 2.
If NAPAS times out during execution, can funds be immediately refunded
to the user? Why or why not? Where do funds reside, and what resolves
the state?

### Task 2.1 --- NAPAS Mock Service (`cafefin-napas-mock`)

**Frontend sub-tasks** - \[ \] Build a developer/operator NAPAS
mock-control screen for triggering success, timeout, silent drop,
duplicate callback, bad signature, and replay scenarios. - \[ \] Keep
mock controls outside normal customer navigation and protect them with a
development/operator role. - \[ \] Sub-task: Create dedicated Spring
Boot module `cafefin-napas-mock` listening on an isolated port. - \[ \]
Sub-task: Implement `POST /mock/napas/outbound` --- process outbound
requests, returning `napasTransactionId`. - \[ \] Sub-task: Implement
`POST /mock/trigger-inbound` --- test helper endpoint triggering inbound
bank transfer webhooks into `cafefin-api`. - \[ \] Sub-task: Implement
`GET /mock/napas/status/{refId}` --- query actual state of mock
transaction. - \[ \] Sub-task: Implement `POST /mock/simulate-failure`
--- test helper enabling configurable failure conditions: timeouts,
duplicate callbacks, bad signatures, payload replays, complete silent
drops (no response).

### Task 2.2 --- Outbound Transfers (CafeFin → NAPAS Gateway)

**Frontend sub-tasks** - \[ \] Build payment status/timeline UI with
explicit `PENDING`, `COMPLETED`, `FAILED`, and `IN_DOUBT` states. - \[
\] Never label timeout as failure; explain that the external result is
indeterminate. - \[ \] Show clearing/settlement progress at a user-safe
level without exposing internal privileged account mechanics. - \[ \]
Provide a refresh/status action that queries the authoritative local
payment status; do not trigger duplicate outbound execution. - \[ \] Add
E2E tests for success, deterministic failure, timeout→IN_DOUBT, and
eventual reconciliation.

**UI slice**: Payment state timeline (`PENDING`, `COMPLETED`, `FAILED`,
`IN_DOUBT`) with clear indication that timeout is indeterminate rather
than an automatic failure.

**Architecture**: Outbound payment interactions utilize the **Saga
Pattern** via manual orchestration. The internal system maintains
double-entry balance equality using the system account `NAPAS_CLEARING`.

Money leaving a closed double-entry system requires an explicit external
counterparty. The system account **`NAPAS_SETTLEMENT`** (seeded in Task
1.2) represents value that has exited to the external network ---
without it, successful outbound payments would violate the global
$\sum = 0$ invariant enforced in Task 1.7.

Executing payments uses a **Two-Phase Transfer** model: 1. Phase 1
(Prepare): DEBIT user account, CREDIT `NAPAS_CLEARING`, create
`NapasTransaction` in `PENDING` state. 2. Phase 2 (Commit/Rollback):
Call external payment gateway: - Success -\> DEBIT `NAPAS_CLEARING`,
CREDIT `NAPAS_SETTLEMENT`, set status `COMPLETED`. The clearing account
nets to zero; settlement carries the exited value, and the global ledger
sum remains zero. - Deterministic Failure (HTTP 4xx/5xx) -\> Trigger
**compensating transaction** (DEBIT `NAPAS_CLEARING`, CREDIT user
account), refunding the user, set status `FAILED`.

⚠️ **Handling Gateway Timeouts & Indeterminate States**: Gateway
timeouts do **NOT** equal payment failures. Reversing funds
automatically on timeout creates double-spend exposures. - Network
timeouts, socket failures, or missing responses transition state to
**`IN_DOUBT`** (or `UNKNOWN`). - Funds **remain locked in
`NAPAS_CLEARING`**. No automatic user refunds occur. - Outbound retries
are prohibited. - Resolution occurs exclusively via active status
polling or scheduled reconciliation.

#### Implementation Tasks

-   [ ] Sub-task: Model `NapasTransaction` entity (`id`, `direction`,
    `status` \[PENDING/COMPLETED/FAILED/IN_DOUBT\], `amount`,
    `accountId`, `napasRefId`). Define and enforce
    uniqueness/cardinality for the external reference so one logical
    payment cannot silently map to multiple external executions.
-   [ ] Sub-task: Implement outbound orchestration workflow in
    `cafefin-api`.
    -   *Verification (Success)*: Outbound success clears
        `NAPAS_CLEARING` into `NAPAS_SETTLEMENT`, sets transaction
        `COMPLETED`, and the global ledger sum remains zero.
    -   *Verification (Deterministic Error)*: Mock 500 error -\> assert
        compensating transfer refunds user, sets transaction `FAILED`,
        balances net to zero.
    -   *Verification (Timeout)*: Mock network timeout -\> confirm
        transaction set to `IN_DOUBT`, user funds remain held in
        `NAPAS_CLEARING`, zero user refunds issued.
    -   *Verification (Status Query)*: Resolve `IN_DOUBT` state via
        `GET /mock/napas/status/{refId}` polling -\> execute post or
        void operations based on verified external gateway state.
-   [ ] Sub-task: Configure Client-Side Timeouts --- set strict socket
    connection and read timeouts on the HTTP client calling
    `napas-mock`.
-   [ ] Sub-task: Integrate Resilience4j Circuit Breaker wrapping
    outbound gateway calls.
    -   Configure isolated test configurations lowering failure rate
        evaluation thresholds (`minimumNumberOfCalls`,
        `slidingWindowSize`).

    -   Configure explicit aspect order ensuring Circuit Breaker wraps
        Retry aspects:

        ``` properties
        resilience4j.circuitbreaker.circuitBreakerAspectOrder=1
        resilience4j.retry.retryAspectOrder=2
        ```

    -   Categorize exception types: domain errors (e.g., "Account Not
        Found") must not record as gateway infrastructure failures.

    -   *Verification*: Simulate persistent gateway down state -\>
        verify circuit transitions to `OPEN`, throwing
        `CallNotPermittedException` immediately on subsequent attempts
        without waiting for socket timeouts.

### Task 2.3 --- Inbound Payment Webhooks (NAPAS → CafeFin)

**Frontend sub-tasks** - \[ \] Build inbound-payment activity/status
view for authorized users/operators. - \[ \] Display
processed/duplicate/rejected webhook outcomes without exposing raw
signatures, HMAC secrets, or sensitive payloads. - \[ \] Add UI evidence
for duplicate callback idempotency and replay rejection in
developer/operator views.

**UI slice**: Inbound payment visibility and webhook processing status
for demo/operator inspection; raw signatures/secrets are never exposed.

**Security Protocol**: Follow Stripe Webhook verification guidelines.
Signatures include timestamps to prevent replay attacks.

-   [ ] Sub-task: Model `NapasWebhookLog` (`id`, `napasTransactionId`
    \[UNIQUE\], `payload`, `signature`, `status`, `processedAt`).
-   [ ] Sub-task: Implement `POST /api/v1/webhooks/napas` in
    `cafefin-api`. The webhook endpoint is explicitly outside JWT
    authentication and is protected by its HMAC signature/timestamp
    verification. Keep it under `/api/**` so the SPA fallback cannot
    consume the request.
    -   **Verification Steps**:
        1.  Parse raw HTTP request payload bytes directly prior to JSON
            deserialization.
        2.  Extract timestamp (`t`) and signature (`v1`) from HTTP
            headers (format: `t=<epoch>,v1=<hex>`). Compute HMAC-SHA256
            signature over `signedPayload = t + "." + rawBody`.
        3.  Validate timestamp tolerance window
            ($| \text{now} - t | \le 5 \text{ minutes}$).
        4.  Perform constant-time signature comparison using
            `MessageDigest.isEqual()`.
    -   *Verification*: Signature validation failure -\>
        `401 Unauthorized`.
    -   *Verification (Replay Defense)*: Valid signature with expired
        timestamp outside tolerance window -\> **Reject request**.
    -   *Verification (Idempotency)*: Validate `napasTransactionId`
        unique processing check before executing ledger credits.
        Duplicate callbacks return `200 OK` without duplicating ledger
        entries.
    -   *Verification*: Valid inbound transfer -\> DEBIT
        `NAPAS_SETTLEMENT` (value entering from the external network),
        CREDIT target user account. Re-derive against the sign
        convention fixed in Task 1.2 before implementation.

### Task 2.4 --- Automated External Reconciliation

**Frontend sub-tasks** - \[ \] Build reconciliation console showing
`IN_DOUBT` items, last external status check, grace-period/next-check
state, and final resolution. - \[ \] Require operator confirmation for
unresolved/manual cases; do not offer an immediate refund button before
reconciliation policy permits it. - \[ \] Display discrepancy type and
external reference while masking sensitive data. - \[ \] Sub-task:
Implement scheduled reconciliation job querying
`GET /mock/napas/transactions?since=...`. - \[ \] Sub-task: Resolve
discrepancy variants: - Missing locally, confirmed on gateway -\>
Execute catch-up processing. - Local state `IN_DOUBT`, confirmed on
gateway -\> Finalize commit (post). - Local state `IN_DOUBT`, unrecorded
on gateway -\> keep the hold during a defined grace period; only after
the grace period and repeated authoritative checks succeed may the
system void the hold/refund the user. - \[ \] Sub-task: Log unresolvable
discrepancies to `ReconciliationResult` for manual operator review.

------------------------------------------------------------------------

## Layer 3 --- Event-Driven Architecture (Kafka)

**Core Learning Objectives**: Transactional Outbox Pattern, idempotent
message consumption, partition key strategy, and dead letter queue (DLQ)
recovery strategies.

**Self-Assessment Gate**: If the Kafka consumer crashes mid-execution,
are messages lost? Does re-processing create duplicate emails?

### Task 3.1 --- Infrastructure (Kafka KRaft Mode)

**Frontend sub-tasks** - \[ \] No customer-facing UI is required. - \[
\] Add an operator/developer health page for Kafka
connectivity/topic/consumer status if operational endpoints are
exposed. - \[ \] Sub-task: Configure a single-node Kafka 4.x container
in `docker-compose.yml`. KRaft is the only mode --- ZooKeeper support
was removed entirely in Kafka 4.0. - \[ \] Sub-task: Provision topic
`transaction.completed` with 3 partitions. - \[ \] Sub-task: Document
ADR on KRaft mode operation, highlighting differences between
single-node combined controller/broker roles used in local development
versus dedicated controller quorums recommended for production.

### Task 3.2 --- Transactional Outbox Pattern (Producer)

**Frontend sub-tasks** - \[ \] Add an operator outbox/event-health view:
pending, processing, published, stale/failed counts. - \[ \] Never
expose raw event payloads containing sensitive financial data to normal
users. - \[ \] Sub-task: Model `OutboxEvent` entity (`id`,
`aggregatetype`, `aggregateid`, `type`, `payload`, `status`
\[PENDING/PUBLISHED\], `createdAt`). Column names map directly to
Debezium outbox standards. - \[ \] Sub-task: Persist `OutboxEvent`
inside the primary database transaction when transfers transition to
`COMPLETED` (guaranteeing local transaction-outbox atomicity without
dual-writes). - \[ \] Sub-task: Implement outbox poller scheduled job
fetching `PENDING` outbox records, publishing to Kafka, and updating
status to `PUBLISHED`. - *Verification*: Simulate Kafka cluster downtime
during transfer execution -\> verify primary transfer transaction
commits successfully while outbox events remain `PENDING`. Confirm
automatic publication when Kafka resumes service. - \[ \] Sub-task:
Multi-instance Poller Synchronization --- claim pending outbox rows with
`SELECT ... FOR UPDATE SKIP LOCKED LIMIT n`, transition them to an
explicit `PROCESSING`/claimed state, and commit the claim before
performing the Kafka network call. Do not hold database locks open while
calling Kafka. A later transaction marks the event `PUBLISHED`; crash
recovery must safely reclaim stale `PROCESSING` rows and tolerate
duplicate publication through idempotent consumers. - *Verification*:
Run dual poller execution threads -\> confirm zero duplicate message
dispatches or lock acquisition delays. - \[ \] Sub-task: Add composite
database index on `(status, createdAt)`. - \[ \] Sub-task: Set message
key = `accountId` during publication to guarantee sequential message
ordering per account within Kafka partitions. - \[ \] Sub-task:
Configure Kafka Producer properties: set `enable.idempotence=true`,
`acks=all`, and `max.in.flight.requests.per.connection <= 5`.

### Task 3.3 --- Decoupled Consumer (`cafefin-notification`)

**Frontend sub-tasks** - \[ \] Add notification delivery status/history
for authorized operators. - \[ \] Show DLT/failure counts and event IDs
without exposing secrets or unnecessary payload data. - \[ \] Add a safe
retry/re-drive action only for authorized operators if the backend
supports it. - \[ \] Sub-task: Initialize module `cafefin-notification`
with Spring Kafka dependencies. - **Migration rule**: Move
`MailpitEmailProvider` and all delivery logic out of `cafefin-api` in
this task; the API module retains only outbox event publication
thereafter (see Module Evolution Note). - \[ \] Sub-task: Configure
consumer group subscribing to `transaction.completed`. - \[ \] Sub-task:
Implement Idempotent Consumer --- maintain `processedEventId`
persistence table. Inspect prior execution state before initiating email
delivery. - *Verification*: Re-queue identical event ID -\> confirm
email dispatch is skipped.

#### Message Ordering vs. Consumer Liveness Analysis

Blocking retries prioritize strict message order per partition over
consumer throughput. Non-blocking retries (using `@RetryableTopic`)
prevent partition blocking but violate strict event ordering. This
implementation strictly enforces message ordering per account, selecting
**blocking retries paired with Dead Letter Topics (DLT)**.

-   [ ] Sub-task: Configure `DefaultErrorHandler` paired with
    `DeadLetterPublishingRecoverer` routing failed messages to
    `transaction.completed.dlt` after fixed, short retry backoffs.
    -   *Verification*: Inject persistent delivery exception for a
        specific event -\> confirm message moves to DLT after configured
        retries, unblocking subsequent messages on the partition.
-   [ ] Sub-task: Document ordering versus liveness architectural
    trade-offs in an ADR.

------------------------------------------------------------------------

## Layer 4 --- Observability & Operations

**Core Learning Objectives**: Distributed tracing propagation,
structured JSON logging, log aggregation, and metrics collection.

### Task 4.1 --- Distributed Tracing & Structured Logging

**Frontend sub-tasks** - \[ \] Propagate `traceId` into a
developer/operator diagnostic UI where appropriate. - \[ \] Provide a
copyable trace/correlation ID on transaction detail and error screens so
an incident can be investigated without exposing sensitive data. - \[ \]
Sub-task: Add Micrometer Tracing via Actuator for automatic propagation
of `traceId` and `spanId` across HTTP calls and Kafka record headers. -
\[ \] Sub-task: Configure correlation logging patterns in
`application.yml`:
`yaml   logging:     pattern:       correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}] "` -
\[ \] Sub-task: Set tracing sampling probability explicitly for
development (`management.tracing.sampling.probability=1.0`). - \[ \]
Sub-task: Enable structured JSON log formatting:
`yaml   logging:     structured:       format:         console: logstash` -
\[ \] Sub-task: Include custom business attributes using SLF4J Key-Value
API (`logger.atInfo().addKeyValue("transactionId", id).log(...)`). -
*Verification*: Initiate an outbound transfer and trace execution logs
across all services using a single `traceId`. - *Verification*: Verify
trace context propagates across `@Async` boundaries and Kafka topic
boundaries.

### Task 4.2 --- Centralized Log Aggregation (Grafana Loki & Alloy)

**Frontend sub-tasks** - \[ \] Add links/views for operator log
investigation by trace ID and service where the environment permits
it. - \[ \] Do not embed unrestricted Grafana/Loki access into the
customer application. - \[ \] Sub-task: Configure Grafana Loki, Grafana
Alloy, and Grafana instances in `docker-compose.yml`. *(Note: Promtail
reached end of life on March 2, 2026 and is unmaintained; Grafana Alloy
is the successor).* - \[ \] Sub-task: Define `config.alloy` pipeline
collecting log streams from Docker container sockets and pushing to the
Loki endpoint (`http://loki:3100/loki/api/v1/push`). - \[ \] Sub-task:
Configure LogQL queries in Grafana filtering log streams by `traceId`
and service name. - *Verification*: Trigger a webhook signature failure
-\> trace generated error logs in Grafana within seconds.

### Task 4.3 --- Metrics Collection (Prometheus & Grafana)

**Frontend sub-tasks** - \[ \] Build or embed an operations dashboard
for HTTP P99, transfer failures, throughput, and key frontend error-rate
signals. - \[ \] Track frontend-specific metrics such as route load
failures and API error rates without collecting unnecessary PII. - \[ \]
Sub-task: Expose Actuator Prometheus endpoint
(`/actuator/prometheus`). - \[ \] Sub-task: Configure Prometheus
container scraping metrics across application instances. - \[ \]
Sub-task: Build Grafana operational dashboard tracking: HTTP P99
latency, outbound transfer failure rates, and transaction throughput per
minute.

------------------------------------------------------------------------

## Layer 5 --- Compliance, Regulatory & Back-Office

**Core Learning Objectives**: KYC lifecycle design, AML transaction
monitoring, sanctions/PEP screening integration boundaries, compliance
case management, reportable-data lineage, operator workflows, RBAC,
maker-checker controls, and auditable manual interventions.

### Task 5.1 --- KYC Lifecycle

**Frontend sub-tasks** - \[ \] Build customer KYC status,
submission/review state, required-action, rejection/re-review,
expiry/update screens. - \[ \] Never render raw identity documents or
sensitive provider payloads unnecessarily. - \[ \] Show capability
restrictions caused by KYC state with clear, non-sensitive explanations.

-   [ ] Sub-task: Model KYC profile, verification status, verification
    attempts, provider reference, timestamps, and review reason.
-   [ ] Sub-task: Define explicit KYC state transitions (`PENDING`,
    `IN_REVIEW`, `VERIFIED`, `REJECTED`, `EXPIRED`, `REQUIRES_UPDATE`).
-   [ ] Sub-task: Keep identity-provider integration behind a provider
    interface; use a deterministic local mock/sandbox for development.
-   [ ] Sub-task: Prevent restricted financial capabilities until
    required KYC state is satisfied.
-   [ ] Sub-task: Store only the minimum demo data required; never log
    raw identity documents or sensitive identity payloads.
-   [ ] Verification: E2E tests cover happy path, rejection, re-review,
    expiry/update, and unauthorized escalation attempts.

### Task 5.2 --- AML & Transaction Monitoring

**Frontend sub-tasks** - \[ \] Build customer-facing compliance
restriction/notice states where appropriate. - \[ \] Build authorized
investigator alert/case screens: risk signal summary, case state, notes,
disposition, and audit history. - \[ \] Do not expose internal AML rules
or sensitive detection signals to customers.

-   [ ] Sub-task: Model AML rules, risk signals, alerts, cases,
    dispositions, and investigator notes.
-   [ ] Sub-task: Implement deterministic demo rules for velocity,
    unusual transaction patterns, repeated failures, and configurable
    thresholds.
-   [ ] Sub-task: Support sanctions/PEP screening through an explicit
    external-provider boundary; use a mock/sandbox dataset for local
    execution.
-   [ ] Sub-task: Ensure AML decisions never mutate immutable ledger
    history; they create compliance records/actions around financial
    events.
-   [ ] Verification: Generate a known suspicious scenario and prove the
    alert, case, audit trail, and operator disposition are persisted.

### Task 5.3 --- Regulatory Reporting

**Frontend sub-tasks** - \[ \] Build authorized reporting console:
reporting period, report type, snapshot/version, validation status,
generation status, and artifact download. - \[ \] Show lineage/reference
identifiers rather than dumping raw personal/transaction data into the
browser.

-   [ ] Sub-task: Define a reporting model separate from transactional
    write models.
-   [ ] Sub-task: Define report metadata: reporting period,
    jurisdiction, report type, data snapshot/version, generation
    timestamp, and validation status.
-   [ ] Sub-task: Build deterministic report generation from ledger,
    account, KYC, and AML data with traceable lineage back to source
    records.
-   [ ] Sub-task: Export a documented machine-readable demo format (for
    example CSV/JSON) and retain immutable report artifacts plus audit
    metadata.
-   [ ] Sub-task: Validate report totals against
    reconciliation/invariants before marking a report ready.
-   [ ] Verification: Re-run the same reporting period against the same
    snapshot and prove deterministic output.

### Task 5.4 --- Back-Office Operations

**Frontend sub-tasks** - \[ \] Build back-office console with role-aware
navigation and server-enforced authorization. - \[ \] Add
customer/account/transaction search, account freeze/unfreeze, manual
review, refund review, reconciliation case handling, and IN_DOUBT
investigation screens. - \[ \] Implement maker-checker UI: initiator
cannot approve their own high-risk action; show pending approval and
immutable audit history. - \[ \] Require explicit reason/confirmation
for financial-impacting actions.

-   [ ] Sub-task: Build a protected operator console with role-based
    permissions (`SUPPORT`, `OPERATIONS`, `COMPLIANCE`, `ADMIN`).
-   [ ] Sub-task: Implement search by customer, account, transaction,
    external reference, idempotency key, and trace ID.
-   [ ] Sub-task: Add explicit `Account.status` (`ACTIVE`, `FROZEN`,
    `CLOSED`) and define which operations are allowed for each status.
-   [ ] Sub-task: Implement operational actions such as account
    freeze/unfreeze, manual review, reconciliation case handling, refund
    review, and `IN_DOUBT` investigation.
-   [ ] Sub-task: Add maker-checker controls for high-risk manual
    actions; the initiator cannot self-approve.
-   [ ] Sub-task: Record immutable operator audit events: actor, action,
    target, reason, before/after state, timestamp, and trace ID.
-   [ ] Verification: Every manual financial-impacting action leaves a
    complete audit trail and respects RBAC/maker-checker constraints.

**UI requirement**: Back-office screens are part of this layer, not a
separate future frontend project.

------------------------------------------------------------------------

## Layer 6 --- High Availability & Disaster Recovery

**Core Learning Objectives**: elimination of single points of failure,
backup/PITR, restore testing, RPO/RTO, failover behavior, operational
runbooks, and proving recovery rather than merely documenting it.

### Task 6.1 --- Application HA

**Frontend sub-tasks** - \[ \] Ensure auth/session behavior works across
multiple API instances without relying on in-memory frontend
assumptions. - \[ \] Add user-visible maintenance/degraded-state
handling and retry/backoff for transient API failures. - \[ \] Run
Playwright through the load balancer and fail one instance during an
authenticated workflow.

-   [ ] Sub-task: Run at least two `cafefin-api` instances behind a
    reverse proxy/load balancer.
-   [ ] Sub-task: Externalize instance-local state; session/auth state
    must remain compatible with multi-instance deployment.
-   [ ] Sub-task: Add readiness/liveness health checks and graceful
    shutdown.
-   [ ] Sub-task: Verify one application instance can fail while traffic
    continues through another healthy instance.

### Task 6.2 --- PostgreSQL Backup & PITR

**Frontend sub-tasks** - \[ \] No customer-facing restore controls. - \[
\] Add operator verification views for last backup/restore drill status
and post-restore reconciliation evidence.

-   [ ] Sub-task: Define backup policy, retention, encryption/storage
    assumptions, and documented RPO/RTO targets for the demo
    environment.
-   [ ] Sub-task: Implement a backup/restore workflow and a controlled
    point-in-time recovery exercise.
-   [ ] Sub-task: Verify restored ledger invariants, Flyway schema
    state, and application consistency after restore.
-   [ ] Verification: A scheduled restore drill succeeds from an actual
    backup artifact; do not treat backup creation alone as proof of DR.

### Task 6.3 --- Failure & Recovery Runbooks

**Frontend sub-tasks** - \[ \] Add a lightweight status/incident banner
mechanism driven by backend health/readiness state. - \[ \] Document
frontend behavior for API, database, Kafka, and provider outages: avoid
duplicate submissions and preserve safe retry semantics.

-   [ ] Sub-task: Document failure scenarios for API instance loss,
    PostgreSQL restart/failure, Kafka unavailability, and
    dependent-service outages.
-   [ ] Sub-task: Define operator actions, expected recovery sequence,
    RPO/RTO measurement, and post-recovery reconciliation.
-   [ ] Verification: Execute the runbooks in a disposable environment
    and attach evidence to the relevant ADR/operations documentation.

------------------------------------------------------------------------

## Layer 7 --- Multi-Region Resilience

**Core Learning Objectives**: regional failure boundaries, traffic
steering, data ownership, replication, split-brain avoidance,
consistency trade-offs, and financial reconciliation after failover.

### Task 7.1 --- Multi-Region Architecture Decision

**Frontend sub-tasks** - \[ \] Define region-aware routing/session
behavior at the frontend boundary without allowing the browser to choose
a financial write authority. - \[ \] Expose only safe
region/service-status information to operators.

-   [ ] Sub-task: Document an ADR comparing active-active,
    active-passive, and primary-region/DR-region models for the CafeFin
    ledger.
-   [ ] Sub-task: Explicitly define which data can be asynchronously
    replicated and which operations require a single authoritative write
    region.
-   [ ] Sub-task: Define regional failover conditions,
    fencing/leadership rules, and stale-write protection.

### Task 7.2 --- Regional Deployment Simulation

**Frontend sub-tasks** - \[ \] Run E2E failover tests through the
traffic-routing layer. - \[ \] Verify an interrupted transfer is shown
as indeterminate rather than automatically retried by the browser.

-   [ ] Sub-task: Run two isolated regional stacks locally or in a
    disposable environment.
-   [ ] Sub-task: Implement traffic routing/failover and a controlled
    regional outage scenario.
-   [ ] Sub-task: Prove that ledger writes do not split across two
    active authorities during a partition/failover event.
-   [ ] Verification: Fail one region, restore service in the surviving
    region, and reconcile financial state before normal operations
    resume.

### Task 7.3 --- Post-Failover Reconciliation

**Frontend sub-tasks** - \[ \] Build operator reconciliation/failover
event screens showing region, authority/fencing state, divergence, and
recovery status. - \[ \] Require explicit recovery state before normal
financial actions are presented as available.

-   [ ] Sub-task: Extend reconciliation to compare region-local state,
    external references, and event offsets.
-   [ ] Sub-task: Record every failover/fencing/recovery event in
    operational audit logs.
-   [ ] Verification: Inject a controlled divergence and prove
    reconciliation detects it without silent auto-remediation.

------------------------------------------------------------------------

## Layer 8 --- Real / Sandbox Bank & Card Integrations

**Core Learning Objectives**: provider adapters, credentials and
signing, external state machines, settlement semantics, webhook
contracts, provider-specific idiosyncrasies, and reconciliation against
real/sandbox external systems.

### Task 8.1 --- Provider Abstraction

**Frontend sub-tasks** - \[ \] Keep provider-specific fields out of
reusable customer components; map them to normalized internal payment
models. - \[ \] Create a provider-agnostic payment UI contract for
status, errors, references, and capabilities.

-   [ ] Sub-task: Separate `NapasClient`/gateway-specific code behind a
    provider adapter boundary so external provider contracts do not leak
    into the core ledger domain.
-   [ ] Sub-task: Define normalized internal payment states and map
    provider-specific states explicitly.
-   [ ] Sub-task: Keep provider credentials in environment/secret
    configuration only; never in source control or logs.

### Task 8.2 --- Bank Transfer Sandbox Integration

**Frontend sub-tasks** - \[ \] Build bank-transfer sandbox payment
initiation/status/reconciliation UI using normalized internal states. -
\[ \] Show provider reference only where appropriate and mask
credentials/secrets completely. - \[ \] Add E2E
authorization/status/webhook/reconciliation flows against the sandbox
environment.

-   [ ] Sub-task: Select one accessible bank/payment-provider sandbox
    and implement an actual outbound/inbound integration path using its
    documented sandbox API.
-   [ ] Sub-task: Implement provider authentication, signatures, webhook
    handling, timeout behavior, and external-reference persistence.
-   [ ] Sub-task: Reconcile provider status with local `IN_DOUBT`
    transactions.
-   [ ] Verification: Execute a sandbox payment end-to-end and reconcile
    local ledger state against the provider's authoritative status.

### Task 8.3 --- Card Payment Sandbox Integration

**Frontend sub-tasks** - \[ \] Build card payment UI for authorization →
capture → refund, with explicit state transitions and safe retry
behavior. - \[ \] Never store raw PAN/CVV or sensitive authentication
data in frontend state, logs, analytics, or local storage. - \[ \] Add
sandbox-only Playwright coverage for duplicate webhook/event handling
and refund idempotency.

-   [ ] Sub-task: Select one accessible card-payment sandbox/provider
    and implement authorization/capture/refund flows behind a provider
    adapter.
-   [ ] Sub-task: Model provider-specific states without polluting the
    core ledger state machine.
-   [ ] Sub-task: Implement webhook verification and duplicate-event
    handling.
-   [ ] Verification: Run authorization → capture → refund and prove
    ledger entries, idempotency, and external references remain
    consistent.

### Task 8.4 --- Production Integration Boundary

**Frontend sub-tasks** - \[ \] Add a production-boundary documentation
page/ADR describing frontend security, CSP, cookie/token strategy,
third-party scripts, analytics, PCI scope, and deployment controls that
would need review before real production use.

-   [ ] Sub-task: Document exactly what additional contractual,
    security, certification, compliance, operational, and network
    controls would be required before replacing a sandbox provider with
    a real production connection.
-   [ ] Sub-task: Keep production provider implementation replaceable;
    do not embed sandbox assumptions into core domain logic.

------------------------------------------------------------------------

## Production-Readiness Risk Assessment

CafeFin is intentionally a **production-oriented engineering/learning
simulation**, not a licensed financial service. The following assessment
describes gaps that become relevant if the project is ever considered
for real-money or production financial use.

  -----------------------------------------------------------------------------
  Risk                    Severity                Assessment
  ----------------------- ----------------------- -----------------------------
  **Legal / Licensing**   **Very High**\*         CafeFin has no claim to be a
                                                  licensed payment service or
                                                  credit institution.
                                                  Real-money deployment would
                                                  require a
                                                  jurisdiction-specific legal
                                                  entity, regulatory-role
                                                  analysis, and applicable
                                                  licensing/authorization.

  **Compliance /          **Very High**           KYC/AML, sanctions/PEP,
  Regulatory**                                    regulatory reporting, and
                                                  maker-checker are implemented
                                                  only at the documented
                                                  demo/simulation scope. They
                                                  are not evidence of
                                                  regulatory compliance or
                                                  audit readiness.

  **Security /            **High**                The roadmap contains strong
  Certification**                                 engineering controls, but
                                                  there is no evidence of
                                                  independent penetration
                                                  testing, security assessment,
                                                  applicable PCI DSS
                                                  validation/attestation,
                                                  formal key-management/HSM
                                                  controls, or production
                                                  key-rotation procedures.

  **Bank / Card           **High**                Layer 8 targets
  Production                                      sandbox/provider
  Integration**                                   integrations. Production
                                                  connectivity would
                                                  additionally require provider
                                                  contracts,
                                                  certification/approval,
                                                  credential and signing-key
                                                  controls, network controls,
                                                  settlement/reconciliation,
                                                  dispute handling, and
                                                  operational agreements.

  **Operational           **High**                HA/DR, runbooks, RPO/RTO, and
  Readiness**                                     multi-region behavior are
                                                  engineering exercises in
                                                  local/disposable
                                                  environments. There is no
                                                  evidence of production
                                                  on-call, 24×7 operations,
                                                  capacity planning, incident
                                                  management, or production
                                                  chaos/failure exercises.

  **Financial Liability / **Very High**\*         A real-money service would
  Safeguarding**                                  need an identified legal
                                                  entity,
                                                  customer-funds/safeguarding
                                                  and settlement model,
                                                  financial-liability
                                                  framework, and any
                                                  jurisdiction-specific
                                                  capital, reserve, insurance,
                                                  or guarantee requirements
                                                  that apply.

  **Scalability /         **Medium**              Pessimistic locking creates a
  Performance**                                   known hot-account
                                                  serialization bottleneck. The
                                                  roadmap includes contention
                                                  benchmarking, but there is
                                                  not yet production-scale
                                                  capacity evidence or a
                                                  multi-region performance
                                                  model.

  **Privacy / Data        **Medium--High**        PII masking and data
  Governance**                                    minimization are present, but
                                                  production use would also
                                                  require data classification,
                                                  retention/deletion policies,
                                                  access governance, privacy
                                                  controls, and
                                                  jurisdiction-specific
                                                  cross-border/data-residency
                                                  analysis.

  **Fraud / Abuse**       **High**                Demo AML rules cover selected
                                                  velocity/pattern scenarios
                                                  but do not establish
                                                  production fraud prevention,
                                                  account-takeover controls,
                                                  behavioral risk, beneficiary
                                                  risk, or real-time fraud
                                                  decisioning.

  **Third-Party / Vendor  **High**                Provider adapters isolate
  Risk**                                          external contracts, but
                                                  production use would require
                                                  vendor due diligence, SLA/SLO
                                                  expectations,
                                                  dependency-failure
                                                  procedures, credential
                                                  rotation, migration plans,
                                                  and contractual incident
                                                  handling.

  **Financial             **High**                Technical reconciliation and
  Reconciliation /                                `IN_DOUBT` handling are
  Business Continuity**                           modeled, but production
                                                  operation would also require
                                                  aged-break thresholds,
                                                  escalation paths, manual
                                                  reconciliation ownership,
                                                  incident SLAs, and controlled
                                                  financial remediation
                                                  procedures.
  -----------------------------------------------------------------------------

\* Severity depends on the target jurisdiction, business model, legal
entity, and whether CafeFin ever handles real customer funds. These rows
are not a legal conclusion.

**Positioning rule:** The README and demo materials must describe
CafeFin as a **production-oriented fintech simulation**. Do not describe
it as a production-ready payment platform, licensed financial service,
PCI-certified environment, or approved bank-network integration.

------------------------------------------------------------------------

## Expanded Target Architecture

The roadmap now treats CafeFin as a staged fintech platform simulation
with a thin same-origin frontend over the core Spring Boot application
and selected surrounding services.

    Browser
       │ HTTPS
       ▼
    ┌───────────────────────────────────────────────┐
    │              CafeFin Core App                 │
    │                                               │
    │  React + TypeScript (Vite build output)       │
    │  Spring Boot REST API                         │
    │  Auth / Account / Ledger / Transfer           │
    │  Idempotency / Compliance / Back-office       │
    └──────────────┬──────────────┬─────────────────┘
                   │              │
                   ▼              ▼
            PostgreSQL          Kafka
                   │              │
                   │              ▼
                   │      cafefin-notification
                   │              │
                   │           Mailpit
                   │
                   ▼
          Reconciliation / Reporting
                   │
         ┌─────────┼──────────┐
         ▼         ▼          ▼
     NAPAS Mock   Bank/      Card
                  Provider   Provider
         
    Additional platform concerns:
      HA / DR / Backup-PITR / RPO-RTO
      Multi-region authority + failover
      Observability: Tracing / Loki / Prometheus / Grafana

**Architecture rule**: The frontend is a first-class client surface from
the beginning, while NAPAS mock, Kafka notification, and external
financial providers remain separately deployable boundaries where the
learning objective is distributed integration rather than process
consolidation.

------------------------------------------------------------------------

## Demo Definition of Done

The first formal demo milestone is **Task 0 + Layer 1 + Layer 2**. Layer
3--4 are optional demo enhancements; Layers 5--8 are outside the
first-demo scope.

### Mandatory demo flows

-   [ ] Fresh `docker compose up` starts the documented demo stack.
-   [ ] Demo users/accounts and opening balances are seeded through
    valid double-entry ledger transactions.
-   [ ] Login/refresh flow works from the React UI.
-   [ ] User can view account balance and transaction history.
-   [ ] User can perform a normal transfer through the React UI.
-   [ ] Concurrent transfer test proves no double-spend/lost update.
-   [ ] Reusing the same `Idempotency-Key` replays the original result.
-   [ ] Same key with a different request fingerprint returns the
    documented conflict response.
-   [ ] Same key used by different users is isolated by user scope.
-   [ ] NAPAS deterministic success completes the external payment.
-   [ ] NAPAS deterministic failure compensates the local hold.
-   [ ] NAPAS timeout transitions to `IN_DOUBT` without automatic
    refund.
-   [ ] Reconciliation resolves `IN_DOUBT` only from authoritative
    gateway state and after the defined grace-period rules.
-   [ ] Inbound webhook verifies HMAC/timestamp, rejects replay, and
    processes duplicate callbacks idempotently.
-   [ ] Frontend visibly distinguishes `PENDING`, `COMPLETED`, `FAILED`,
    and `IN_DOUBT`.
-   [ ] No privileged SYSTEM-account operation is exposed through normal
    user UI.
-   [ ] Playwright covers the primary end-to-end demo journey.

------------------------------------------------------------------------

## Definition of Done (Quality Gate)

The repository is considered **roadmap-complete and production-oriented
within its documented demo scope** when:

-   [ ] A fresh environment clone builds and runs successfully via
    `docker compose up` without manual setup interventions.
-   [ ] CI (GitHub Actions) is green on `main`: backend verification and
    frontend build/test pipelines pass.
-   [ ] The full automated suite executes with zero failures, including
    backend concurrency/deadlock/idempotency tests, frontend component
    tests, and Playwright E2E flows. No tests may be marked `@Disabled`
    to pass builds.
    -   *Allowed Exception*: Demonstration failure tests (e.g., Task
        1.3b lockless lost update) isolated via explicit JUnit tags
        (`@Tag("demo")`).
-   [ ] The core financial invariants are continuously verified:
    double-entry balance, immutable ledger, `balanceAfter` consistency,
    idempotency uniqueness, reconciliation, and external-reference
    consistency.
-   [ ] Production hardening constraints are validated: database
    triggers reject invalid schema mutations, zero PII/credentials leak
    into log outputs, rate limiters return HTTP 429, operator actions
    are RBAC/maker-checker protected, and audit events are complete.
-   [ ] The React frontend is served by the same-origin Spring Boot core
    application in the demo deployment, while the frontend source
    remains independently buildable.
-   [ ] Every completed backend task has an explicit frontend
    surface/state/security/verification decision; customer-facing
    vertical slices are not considered complete until their UI and
    Playwright coverage are complete.
-   [ ] Frontend financial values are handled as decimal/string
    representations without authoritative client-side floating-point
    calculations.
-   [ ] Authentication, authorization, idempotency, retry, `IN_DOUBT`,
    RFC 9457 errors, and irreversible actions have explicit UI behavior
    and automated coverage.
-   [ ] Mailpit is used only as the development/demo notification sink;
    the provider abstraction is verified independently of the Mailpit
    implementation.
-   [ ] KYC, AML, regulatory reporting, and back-office workflows have
    automated happy-path and failure-path verification, including
    auditable manual actions.
-   [ ] HA/DR targets have explicit RPO/RTO definitions and at least one
    successful backup restore/failover drill with post-recovery
    reconciliation evidence.
-   [ ] Multi-region architecture has an ADR, a documented
    authority/fencing model, and a controlled failover simulation
    demonstrating that financial writes do not split across competing
    authorities.
-   [ ] At least one bank/payment-provider sandbox integration and one
    card-payment sandbox integration are implemented through provider
    adapters, with webhook verification and reconciliation.
-   [ ] Architecture Decision Records (ADRs) exist in `docs/adr/`
    covering key technical choices:
    -   Pessimistic vs. Optimistic Lock Selection.
    -   Transaction Isolation Levels (`READ COMMITTED` + explicit
        locks).
    -   Idempotency Claim Transaction Boundaries.
    -   Ledger Immutability Enforcement (Revocation vs. Triggers).
    -   Indeterminate State Management (`IN_DOUBT` vs. Immediate
        Failures).
    -   Kafka Consumer Partition Ordering vs. Availability.
    -   Outbox Pattern Implementation Strategy (Poller vs. CDC).
    -   Third-party Mocking Rationale.
    -   Virtual Threads Adoption or Deferral.
    -   JWT Signing Algorithm (Asymmetric vs. Symmetric).
    -   Rate Limiter Topology (In-Memory Single-Instance vs. Shared
        Backend).
    -   Idempotency Conflict Semantics (`422` fingerprint mismatch ---
        documented deviation from Stripe's `400 idempotency_error`).
    -   Frontend/backend same-origin monolith deployment.
    -   KYC/AML provider boundaries and data minimization.
    -   Regulatory-report data lineage and reproducibility.
    -   Back-office RBAC and maker-checker controls.
    -   HA/DR RPO/RTO targets and restore strategy.
    -   Multi-region authority/fencing strategy.
    -   Real/sandbox bank and card provider adapter boundaries.
-   [ ] Sequence diagrams exist in project documentation illustrating
    complex flows: Outbound Payments with Compensation, Outbox Message
    Publishing, KYC/AML review, `IN_DOUBT` resolution, HA/DR failover,
    and multi-region failover/fencing.
-   [ ] Contention benchmark results (Task 1.3c) are recorded and
    attached to the locking ADR.
-   [ ] All self-assessment questions across layers can be fully
    answered without referencing source code.
-   [ ] The README clearly distinguishes **engineering/demo
    completeness** from legal/regulatory certification, licensed
    financial operation, PCI certification, and production bank
    connectivity approval.

------------------------------------------------------------------------

## Reference Sources & Decision Citations

Citations are classified into tiers. Architectural decisions in ADRs
must cite **Tier A** sources directly.

### Tier A --- Official Documentation (Verified Core Specifications)

  -----------------------------------------------------------------------
  Assertion / Technical Mechanism     Source Documentation
  ----------------------------------- -----------------------------------
  Lock Modes,                         Hibernate ORM User Guide ---
  `OPTIMISTIC_FORCE_INCREMENT`        *Locking Chapter*
  versioning, `PESSIMISTIC_WRITE`     
  mechanics                           

  JPA Lock Timeout Hints (`0` =       Hibernate ORM --- `Timeouts.java` /
  NOWAIT, `-2` = SKIP LOCKED)         Locking Specifications

  `@Lock(LockModeType...)`,           Spring Data JPA Reference
  `@QueryHints` Usage                 Documentation

  `READ COMMITTED`: `FOR UPDATE` wait PostgreSQL Documentation ---
  mechanics and re-evaluation of      *Section 13.2.1 Transaction
  `WHERE` clauses                     Isolation*

  `REPEATABLE READ`: Serialization    PostgreSQL Documentation ---
  Failures (`40001`) and necessity of *Transaction Isolation*
  explicit locks                      

  Deadlock Auto-Detection and         PostgreSQL Documentation ---
  Ordering Defense;                   *Explicit Locking / Runtime Config*
  `deadlock_timeout` defaults         

  Object Owners Retain Implicit       PostgreSQL Documentation --- *SQL
  Privileges (Inefficacy of `REVOKE`  GRANT/REVOKE & Schema Privileges*
  from Owners)                        

  RFC 9457 Problem Details            Spring Boot Reference Documentation
  Configuration & Spring MVC          --- *Web Servlet Properties*
  Auto-Configuration                  

  Micrometer Tracing & Structured     Spring Boot Reference Documentation
  JSON Logging Capabilities           --- *Observability & Logging*

  Testcontainers `@ServiceConnection` Spring Boot & Testcontainers JUnit
  and static container lifecycle      5 Integration Guides
  rules                               

  Jackson `BigDecimal` String         Jackson Databind Documentation &
  Serialization                       Serializer Source
  (`@JsonFormat(shape=STRING)`)       

  Kafka Idempotent Producer Defaults  Apache Kafka Operations & Producer
  (`enable.idempotence=true`,         Configuration Guides
  `acks=all`)                         

  Kafka KRaft Architecture and        Apache Kafka Documentation ---
  Combined vs. Isolated Controller    *KRaft Operations (`kraft.md`)*
  Roles                               

  Debezium Outbox Event Router Schema Debezium Documentation --- *Outbox
  Specifications                      Event Router Transformations*

  Idempotency Key Lifecycles,         Stripe API Reference ---
  Fingerprinting, and Conflict        *Idempotent Requests Guide*
  Handling                            

  Webhook HMAC Signatures,            Stripe API Documentation ---
  Timestamps, and Replay Tolerances   *Webhook Signature Verification*

  Resilience4j Circuit Breaker Aspect Resilience4j Spring Boot 3
  Ordering and Defaults               Configuration Documentation

  Flyway Migration Checksum Mechanics Flyway Documentation --- *Migration
  and Repair Workflows                Lifecycle & Validation*

  Double-Entry Invariants and         TigerBeetle Architecture
  Two-Phase Transfer Mechanics        Documentation --- *Debit/Credit
                                      Concepts*

  Saga Orchestration, Deadlines, and  Axon Framework Reference Guide ---
  Compensating Actions                *Sagas & Deadlines*

  React Router / TanStack Query /     Official project documentation ---
  Orval / React Hook Form / Zod /     re-verify versions/configuration at
  Playwright / Vitest / MSW           project start
  capabilities                        

  React + TypeScript SPA same-origin  Spring Boot static resource
  deployment with Spring Boot static  documentation + React/Vite build
  resources                           documentation --- verify exact
                                      packaging setup at project start

  KYC / AML / regulatory reporting    Jurisdiction-specific
  implementation obligations          regulator/provider documentation
                                      --- **must be selected and verified
                                      for the target jurisdiction before
                                      implementation**

  Promtail EOL (March 2, 2026) &      Grafana Loki / Alloy Documentation
  Grafana Alloy Migration             --- *Migrate from Promtail*

  Virtual Threads & Pinning Semantics OpenJDK JEP Index
  (JEP 444, JEP 491)                  

  Kafka 4.0 ZooKeeper Removal &       Apache Kafka 4.0 Release
  KRaft-Only Operation                Announcement
  -----------------------------------------------------------------------

### Tier B --- Technical Articles & Community Standards (Context Only)

-   Derived Balance Patterns & Double-Entry Ledger Implementations.
-   Row-Level Locking Patterns in Financial Wallet Engines.
-   Atomic Idempotency Claim Design Patterns.
-   Multi-Module Spring Boot Project Layouts.

### Tier B+ --- Multi-Language Implementation Consensus

-   **Monetary Integer Representation**: Uniform usage of integer minor
    units across financial libraries (`go-money`, `RubyMoney`,
    `moneyphp`, `Brick\Money`).

------------------------------------------------------------------------

## Operational Execution Notes

-   Each sub-task is considered complete **only when accompanying
    automated tests pass**.

-   Do not advance to subsequent layers while unverified tasks or
    unexplained mechanisms remain in the active layer.

-   Technical assertions must be verified against official documentation
    (Tier A). Unverified claims must be flagged explicitly as requiring
    verification prior to implementation.

-   ## Safety and integrity constraints must be enforced at the lowest execution layer possible (Database Constraints \> Application Logic).

## Fintech Challenges and How CafeFin Has Demonstrated Solutions

  ------------------------------------------------------------------------------------------------
  \#                Fintech Challenge  Why It Matters      How CafeFin Demonstrates the Solution
  ----------------- ------------------ ------------------- ---------------------------------------
  1                 **Monetary         Floating-point      Integer minor units per ISO 4217;
                    precision &        drift silently      `BigDecimal` string construction; JSON
                    representation**   corrupts balances;  string serialization; `Money` value
                                       currencies differ   object; round-trip serialization tests
                                       in scale (VND=0,    (Task 0.6).
                                       USD=2, BHD=3).      

  2                 **Double-spend &   Two concurrent      Sorted pessimistic row locks
                    race conditions**  debits against one  (`FOR UPDATE`), overdraft check after
                                       balance can both    lock acquisition, `CompletableFuture`
                                       succeed, creating   concurrency tests, cross-transfer
                                       money from nothing. deadlock tests, and a `@Tag("demo")`
                                                           test proving the lost update *without*
                                                           locks (Tasks 1.3, 1.3b).

  3                 **Safe retries     Clients retry on    Atomic `INSERT ... ON CONFLICT`
                    (idempotency)**    timeout; naive      user-scoped key claim,
                                       re-execution        `IN_FLIGHT`/`COMPLETED` lifecycle,
                                       duplicates          response replay, fingerprint conflict
                                       transfers.          detection, lease-based crash recovery,
                                                           and unique user-scoped
                                                           transaction/idempotency association
                                                           (Task 1.3).

  4                 **Auditability &   Regulators and      Append-only ledger; derived +
                    tamper             disputes require    denormalized balances with
                    resistance**       immutable history;  `entrySequence`; DB-level `REVOKE` +
                                       application bugs    triggers blocking `UPDATE`/`DELETE`;
                                       must not rewrite    dual migration/runtime DB users (Tasks
                                       it.                 1.2, 1.8.1, 0.3).

  5                 **Consistency      Drift between       Global $\sum = 0$ invariant,
                    across the         entries and         per-transaction balance checks,
                    ledger**           balances is the     scheduled internal reconciliation
                                       classic wallet      proven against an injected imbalance
                                       defect.             (Task 1.7).

  6                 **Distributed      The network can     Saga orchestration with
                    transactions with  fail *between*      `NAPAS_CLEARING`/`NAPAS_SETTLEMENT`,
                    external           debit and external  compensating transactions for
                    gateways**         confirmation; a     deterministic failures, `IN_DOUBT`
                                       timeout is not a    state with funds held, resolution only
                                       failure.            via status polling and external
                                                           reconciliation (Tasks 2.2, 2.4).

  7                 **Untrusted        Forged or replayed  HMAC-SHA256 over raw bytes, timestamp
                    inbound            webhooks equal free tolerance window, constant-time
                    integrations**     money.              comparison, unique-ID idempotent
                                                           processing (Task 2.3).

  8                 **Partner          A slow or down      Strict client timeouts, Resilience4j
                    instability**      gateway must not    circuit breaker with verified `OPEN`
                                       cascade into the    fail-fast behavior,
                                       core.               domain-vs-infrastructure error
                                                           classification (Task 2.2).

  9                 **Reliable event   Dual writes (DB +   Transactional outbox with `SKIP LOCKED`
                    delivery**         broker) lose or     multi-instance poller, idempotent Kafka
                                       duplicate events.   producer, per-account partition keys,
                                                           idempotent consumer, DLT with
                                                           documented ordering-vs-liveness
                                                           trade-off (Layer 3).

  10                **Account takeover Credential attacks  BCrypt hashing, hashed refresh tokens
                    & abuse**          and token theft     with rotation and family-wide breach
                                       target money        revocation, asymmetric JWT signing,
                                       directly.           per-key rate limiting with RFC 9457
                                                           `429`s, enumeration-safe error messages
                                                           (Tasks 1.1, 1.8.3, 1.8.4).

  11                **Data leakage**   Logs are the most   Secrets exclusively from environment,
                                       common              fail-closed startup, masking/redaction
                                       PII/credential      verified by grepping `DEBUG`-level logs
                                       exfiltration path.  (Task 1.8.5).

  12                **Operating and    Money incidents     End-to-end `traceId` propagation across
                    diagnosing         demand answering    HTTP, `@Async`, and Kafka; structured
                    incidents**        "where is           JSON logs in Loki; Prometheus/Grafana
                                       transaction X" in   P99 and failure-rate dashboards (Layer
                                       minutes.            4).

  13                **Safe schema      Ad-hoc DDL on a     Immutable Flyway migrations with
                    evolution**        ledger database is  checksum enforcement, naming
                                       unrecoverable.      validation, DDL restricted to the
                                                           migration user (Task 0.3).

  14                **Contested        Fintech decisions   Every major decision captured as an ADR
                    engineering        (locking, ordering, backed by Tier A sources and, for
                    trade-offs**       isolation) have no  locking, measured benchmarks rather
                                       universal answer.   than assertion (Tasks 1.3c, DoD).

  15                **Identity &       Financial systems   KYC lifecycle, AML alerts/cases,
                    compliance**       need controlled     provider boundaries, and auditable
                                       customer onboarding investigation workflows (Layer 5).
                                       and monitoring, not 
                                       just                
                                       authentication.     

  16                **Regulatory       Regulatory outputs  Versioned reporting snapshots,
                    traceability**     must be             validation, lineage, and immutable
                                       reproducible and    report artifacts (Task 5.3).
                                       traceable to        
                                       authoritative       
                                       source records.     

  17                **Human            Real payment        RBAC, maker-checker controls, manual
                    operational        systems require     review, and immutable operator audit
                    intervention**     controlled manual   trails (Task 5.4).
                                       handling for        
                                       exceptions and      
                                       investigations.     

  18                **Availability &   Financial services  Multi-instance HA, backup/PITR, restore
                    disaster           need tested         drills, RPO/RTO, runbooks, and
                    recovery**         continuity and      post-recovery reconciliation (Layer 6).
                                       recoverability, not 
                                       only a healthy      
                                       single instance.    

  19                **Regional failure A regional outage   Authority/fencing model, regional
                    & consistency**    must not create     failover simulation, and post-failover
                                       competing financial reconciliation (Layer 7).
                                       authorities or      
                                       silently diverged   
                                       ledgers.            

  20                **External         Sandbox/mock        Provider adapters with sandbox
                    provider reality** behavior is useful, bank/card integrations, webhook
                                       but real providers  verification, and reconciliation
                                       introduce           against external status (Layer 8).
                                       provider-specific   
                                       contracts and state 
                                       machines.           

  21                **Product          Operators and       React/TypeScript same-origin UI, typed
                    usability around   customers need      OpenAPI client, explicit transfer
                    financial risk**   clear visibility    states, transaction detail, and
                                       into state, errors, back-office workflows built alongside
                                       and irreversible    each backend slice.
                                       actions.            

  22                **Production       A technically       Explicit Demo DoD, production-readiness
                    boundary honesty** strong simulation   risk assessment, sandbox-only
                                       can still be        positioning, and documented
                                       mistaken for a      legal/compliance/security/operational
                                       licensed or         gaps.
                                       production          
                                       financial service.  
  ------------------------------------------------------------------------------------------------

**Honest boundary**: Even after completing this expanded roadmap,
CafeFin remains an engineering/learning platform simulation. It can
demonstrate implementation patterns for KYC/AML, regulatory reporting,
back-office operations, HA/DR, multi-region resilience, and sandbox
bank/card integrations, but it does not by itself establish regulatory
compliance, licensing, PCI certification, production bank-network
approval, or operational readiness for real customer funds. Any
production deployment would require jurisdiction-specific
legal/compliance controls, contracted providers, security assessments,
certified infrastructure/processes, and independently validated
operational procedures.

------------------------------------------------------------------------

## Revision Notes --- v4

This revision preserves the original layered architecture while
addressing the main pre-demo and production-readiness gaps identified
during review:

-   Spring Boot baseline is pinned to a supported 4.1.x line instead of
    retaining 3.5.x as an active option.
-   A separate **Demo Definition of Done** is introduced: Task 0 +
    Layers 1--2, with Layers 3--4 optional and Layers 5--8 outside the
    first demo.
-   Deterministic demo funding is added through valid double-entry seed
    transactions.
-   Idempotency keys are explicitly scoped to the authenticated user.
-   Outbound mutating payment retries are prohibited unless a
    provider-specific idempotency contract is explicitly modeled.
-   NAPAS webhook routing is moved under `/api/v1/webhooks/**` to avoid
    SPA fallback ambiguity.
-   The missing NAPAS reconciliation endpoint is normalized to
    `/mock/napas/transactions`.
-   Refund authorization, idempotency, and partial/full refund limits
    are defined.
-   `IN_DOUBT` reconciliation now requires a defined grace period before
    a missing external record can justify a refund.
-   Outbox publication is changed to claim/commit before network I/O,
    with crash recovery/idempotent-consumer implications documented.
-   SYSTEM-account overdraft policy is made explicit by account class.
-   Account freeze requires an explicit account status model.
-   A production-readiness risk assessment separates legal/regulatory,
    security, operational, scalability, privacy, fraud, vendor, and
    financial-reconciliation risks.
-   Frontend requirements are now attached to every roadmap task,
    including UI surface, state handling, security exposure, and
    verification expectations.
