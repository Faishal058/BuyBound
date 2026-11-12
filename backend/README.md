# BuyBound Backend

Spring Boot (Java 21) modular monolith implementing the BuyBound Guardian-first
purchase flow: watches, versioned spending mandates, candidate-offer intake,
deterministic Guardian policy evaluation, atomic spend reservations, a
clearly-labelled payment-adapter seam, webhook receipt, and an append-only audit
trail. PayPal Sandbox execution is **not** implemented (Gate 0 open) — see §11.

## 1. Purpose and responsibilities

The backend is the trusted authority for everything money-adjacent. The LLM may
discover/rank/explain offers; it never authorizes payments or alters policy.

## 2. Framework, language, runtime

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Build | Maven |
| Web / validation | spring-boot-starter-web, spring-boot-starter-validation |
| Persistence | Spring Data JPA, Flyway, PostgreSQL 16 |
| Tests | JUnit 5, Mockito, MockMvc (`spring-boot-starter-test`) |

## 3. Folder structure

```text
backend/
├── pom.xml
├── Dockerfile
├── README.md                      # this file
├── BACKEND_STRUCTURE.md           # module responsibilities
└── src/
    ├── main/
    │   ├── java/com/buybound/
    │   │   ├── BuyBoundApplication.java
    │   │   ├── config/CorsConfig.java
    │   │   ├── common/exception/   # ApiError, GlobalExceptionHandler, 404/409 mappers
    │   │   ├── health/            # GET /api/health, GET /api/info
    │   │   ├── guardian/          # domain, GuardianEvaluator, DTOs, controller
    │   │   ├── watch/             # Watch + Mandate entities, service, controller
    │   │   ├── purchase/          # intents, decisions, reservations, attempts, service, controller
    │   │   ├── payment/           # PaymentAdapter, StubPaymentAdapter, PayPalPaymentAdapter
    │   │   ├── webhook/           # provider event entity + rejecting-until-Gate-0 controller
    │   │   └── audit/             # AuditEvent, AuditService, AuditController
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       └── db/migration/V1__init.sql
    └── test/java/com/buybound/
        ├── guardian/GuardianEvaluatorTest.java   # 20 pure-logic cases
        ├── guardian/GuardianControllerTest.java  # HTTP slice, real evaluator
        ├── watch/WatchControllerTest.java        # HTTP slice, mocked service
        └── purchase/PurchaseServiceTest.java     # orchestration with mocked repos
```

Module naming follows the v3 intended structure (`watch`, `guardian`, `purchase`,
`payment`, `webhook`, `audit`) adapted to the implemented domain; there is no
separate `discovery` module yet — offer intake lives in `purchase` until the
Channel3 adapter lands (see `docs/IMPLEMENTATION_STATUS.md`).

## 4. Application entry point

`com.buybound.BuyBoundApplication` — standard Spring Boot main class.

## 5. API controllers and routes

All routes match `contracts/openapi.yaml` v0.3.0.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | `{"status":"UP"}` |
| GET | `/api/info` | `{name, version, paymentsMode, discoveryMode}` |
| POST | `/api/watchlist` | Create watch + mandate v1 → 201 |
| GET | `/api/watchlist` | List watches |
| GET | `/api/watchlist/{id}` | Watch details |
| GET | `/api/watchlist/{id}/mandate` | Active mandate |
| GET | `/api/watchlist/{id}/purchase-intents` | Intents, newest first |
| POST | `/api/watchlist/{id}/pause` | Pause future work |
| POST | `/api/watchlist/{id}/revoke` | Revoke mandate immediately |
| POST | `/api/watchlist/{id}/offers` | Submit candidate offer → intent + Guardian decision → 201 |
| GET | `/api/purchase-intents/{id}` | Intent + status |
| GET | `/api/purchase-intents/{id}/guardian-decision` | Bound Guardian decision |
| POST | `/api/purchase-intents/{id}/approve` | HOLD → fresh revalidation → 202 |
| POST | `/api/purchase-intents/{id}/demo-capture` | **Stub mode only**, always simulated |
| POST | `/api/guardian/evaluations` | Stateless policy+offer evaluation |
| GET | `/api/audit-events` | `limit`, `eventType`, `correlationId` filters |
| POST | `/api/webhooks/paypal` | Rejects until webhook verification lands (Gate 0) |

Errors: consistent `{timestamp, status, error, message, path}` via
`GlobalExceptionHandler` (400 validation, 404 not found, 409 conflict).

## 6. Service and business logic

- **GuardianEvaluator** (`guardian/service`): pure function of
  (policy, offer, context). Rules in fixed order, BLOCK > HOLD > ALLOW;
  missing/stale/malformed data fails closed. 15-minute decision TTL.
- **WatchService**: creates watch + mandate v1 atomically; pause/revoke
  (revoke also revokes the mandate and blocks new work immediately).
- **PurchaseService**: offer intake → intent (`OFFER_VALIDATED`) → Guardian
  evaluation (persisted, `GUARDIAN_EVALUATED`) → ALLOW: atomic reservation
  (`SPEND_RESERVED`, UNIQUE constraint on intent) · HOLD: `AWAITING_APPROVAL` ·
  BLOCK: `BLOCKED`. `approve()` re-runs the evaluator on the persisted snapshot —
  approval never bypasses policy. `demoCapture()` only in stub mode.
- **AuditService**: append-only, secrets never written.

## 7. Data models and DTOs

Entities: `Watch`, `Mandate` (+ `mandate_allowed_merchants`), `PurchaseIntent`
(+ embedded `OfferSnapshot`), `GuardianDecisionRecord`, `SpendReservation`
(UNIQUE on intent), `PaymentAttempt`, `ProviderWebhookEvent`, `AuditEvent`.
DTOs mirror `contracts/openapi.yaml` schemas. Money: `BigDecimal` ↔
`NUMERIC(19,4)`; timestamps UTC (`Instant`).

## 8. Database configuration and migrations

PostgreSQL 16 via `infra/docker-compose.yml`. Flyway runs `V1__init.sql` on
startup; JPA `ddl-auto: validate` (schema is migration-owned). Verify on a clean
database: start compose, run the app, check the `flyway_schema_history` table.

## 9. Authentication and authorization

Not implemented — approach undecided (`docs/DECISIONS.md` Q9). Do not expose this
service publicly without adding auth first.

## 10. Validation and error handling

Bean Validation on all request DTOs (`@NotBlank`, `@Pattern` for ISO currency,
`@DecimalMin`, `@Min`); centralized `@RestControllerAdvice`; untrusted external
input is validated before Guardian evaluation, which additionally fails closed.

## 11. PayPal integration

**Not implemented.** `PaymentAdapter` is the seam; `StubPaymentAdapter`
(active only with `PAYMENTS_MODE=stub`) returns results explicitly marked
simulated; `PayPalPaymentAdapter` (active with `PAYMENTS_MODE=paypal`) refuses to
run until Gate 0 verifies the exact product, vault/off-session support, and
merchant relationship. Webhook signature verification is likewise Gate 0-gated:
the endpoint rejects events until then. See `docs/DECISIONS.md` Q1–5.

## 12. AI integration

No LLM provider is configured (open question 8). The deterministic Guardian is
the complete decision-maker; the documented AI boundary (LLM discovers/ranks/
explains under a strict schema, never authorizes) is ready for a provider to be
plugged in behind a separate service when chosen.

## 13. Webhooks

`POST /api/webhooks/paypal` persists nothing today: without `PAYPAL_WEBHOOK_ID`
it returns 409 "not configured"; with it, 409 "signature verification not
implemented (Gate 0)". Deduplication by provider event ID is implemented in the
entity for when verification lands.

## 14. External service integrations

PostgreSQL (required). PayPal, Channel3, LLM provider: all pending Gate 0 /
selection — represented by documented seams and stubs, not live calls.

## 15. Environment variables

See root `.env.example`. Key ones: `DATABASE_URL/USERNAME/PASSWORD`,
`PAYPAL_BASE_URL/CLIENT_ID/CLIENT_SECRET/WEBHOOK_ID`, `CHANNEL3_API_KEY`,
`LLM_API_KEY`, `PAYMENTS_MODE` (`sandbox` default; `stub` enables the simulated
adapter), `DISCOVERY_MODE` (`fixture` default), `APP_CORS_ORIGINS`.

## 16. Installation and startup

Prerequisites: Java 21, Maven 3.9+, Docker Compose.

```bash
cp .env.example .env            # fill local values; never commit .env
docker compose -f infra/docker-compose.yml up -d
cd backend && mvn spring-boot:run
# → http://localhost:8080/api/health
```

With `SPRING_PROFILES_ACTIVE=local` for debug SQL logging.

## 17. Database setup

Handled by Flyway on startup. For a clean slate: `docker compose down -v` then
`up -d`, then start the app.

## 18. Testing

```bash
cd backend
mvn test        # unit + MockMvc slice tests
mvn verify      # full build with tests
```

Suites: `GuardianEvaluatorTest` (20 cases: caps, currency, quantity, merchant
allowlist HOLD/BLOCK, staleness, revocation, velocity, budget, approval
threshold, identity uncertainty, incomplete evidence, precedence, determinism,
null-safety), `GuardianControllerTest` (HTTP through the real evaluator),
`WatchControllerTest` (201/400/404), `PurchaseServiceTest` (ALLOW→reserve,
BLOCK/HOLD paths, paused-watch conflict, approve revalidation, demo-capture
disabled in sandbox mode, simulated capture in stub mode).

## 19. API examples

```bash
# health
curl localhost:8080/api/health
# create a watch
curl -X POST localhost:8080/api/watches -H 'Content-Type: application/json' -d \
 '{"productName":"Sony WH-1000XM5","merchantId":"merchant-a","currency":"USD",
   "targetPrice":"300.00","maxUnitPrice":"350.00","maxTotal":"350.00","maxQuantity":1}'
# stateless Guardian evaluation
curl -X POST localhost:8080/api/guardian/evaluations -H 'Content-Type: application/json' -d \
 '{"policy":{"mandateId":"m1","version":1,"currency":"USD","maxUnitPrice":"350.00",
   "maxTotal":"350.00","maxQuantity":1,"allowedMerchantIds":["merchant-a"],
   "maxPurchasesPerDay":3,"offerMaxAgeSeconds":3600,"newMerchantBehavior":"HOLD",
   "policyVersion":"1.0"},
   "offer":{"merchantId":"merchant-a","canonicalSku":"SKU-1","quantity":1,"currency":"USD",
   "unitPrice":"300.00","totalPrice":"300.00","observedAt":"2026-10-12T10:00:00Z",
   "evidenceHash":"abc"}}'
```

## 20. Security considerations

No secrets in code or logs; `.env` git-ignored; CORS origins configurable;
money in `BigDecimal`; UTC timestamps; Guardian decisions immutable once
persisted; reservations guarded by DB constraints; demo-capture fenced behind an
explicit mode flag and always labelled simulated.

## 21. Deployment

`backend/Dockerfile` builds a runnable image (`mvn package` first). Render is the
documented target (sponsor) — no `render.yaml` yet; see `docs/DEPLOYMENT.md`.

## 22. Known limitations

- No auth; local/dev use only.
- PayPal + Channel3 + LLM provider all pending (documented seams/stubs).
- No background worker yet — offer intake is via the demo endpoint.
- Single active mandate version per watch (versioning schema supports more).
