# Integration Contracts — Consistency Verification

**Verified:** 2026-10-12 (v3 audit). Method: read every contract and cross-reference
field-by-field against the design docs. Anything that cannot be checked yet is marked
**NOT VERIFIABLE**, not assumed.

## 1. OpenAPI ↔ Guardian policy schema — CONSISTENT

- `contracts/openapi.yaml` `Mandate` schema mirrors
  `contracts/guardian-policy.schema.json`: `mandateId`/`version`, `currency`,
  `maxUnitPrice`, `maxTotal`, `maxQuantity`, `allowedMerchantIds`,
  `maxPurchasesPerDay`, `offerMaxAgeSeconds`, `newMerchantBehavior` (HOLD/BLOCK),
  plus optional `requireHumanApprovalAbove`, `allowedCategories`, `maxWeeklySpend`.
- Money is decimal strings in both (`^(0|[1-9][0-9]*)(\.[0-9]{1,2})?$`); no binary
  floating-point anywhere.

## 2. OpenAPI ↔ PAYMENT_FLOW.md — CONSISTENT

- `GuardianDecision` carries the full exact-parameter binding from PAYMENT_FLOW §3:
  intent ID, mandate ID + version, merchant, SKU/variant (via intent), quantity,
  currency, max total, evidence hash, policy version, expiry.
- `PurchaseIntent.status` enum matches the §2 state machine exactly, including
  `RECONCILIATION_REQUIRED` as distinct from `FAILED`.
- `idempotencyKey` is required on the intent (§5: one logical purchase, one key).
- `POST …/approve` documents the §2 HOLD rule: approval triggers revalidation,
  never bypasses policy (`202` / `409`).

## 3. events.md ↔ PAYMENT_FLOW.md — CONSISTENT

- Internal events cover the lifecycle (`purchase_intent.created`,
  `guardian.evaluated`, `spend.reserved`, `purchase.hold`, `purchase.blocked`,
  `payment.*`, `mandate.revoked`, `webhook.*`).
- Processing rules match §6: verify signature first, dedupe by provider event ID,
  validate transitions, fetch provider status on ambiguity, never mark paid from
  frontend claims.

## 4. .env.example ↔ infra/docker-compose.yml — CONSISTENT

- `DATABASE_URL=jdbc:postgresql://localhost:5432/buybound` matches the compose
  service (`buybound-postgres`, db `buybound`, port 5432).
- `PAYPAL_BASE_URL=https://api-m.sandbox.paypal.com` — sandbox only, consistent
  with ADR-003 and the `PAYMENTS_MODE=sandbox` guard flag.
- `DISCOVERY_MODE=fixture` is the honest default until Channel3 access is
  verified (DECISIONS.md open questions 6–7).

## 5. CI ↔ repository — CONSISTENT

- `.github/workflows/ci.yml` references only files that exist (`AGENTS.md`,
  `docs/*.md`, `contracts/*`).
- The `backend` job's `hashFiles('backend/pom.xml') != ''` guard is correct:
  no `pom.xml` exists, so the job stays dormant instead of failing.

## 6. README setup instructions ↔ repository — FIXED in v3

- v2 instructed `mvn test` from `backend/` where no build file exists. Corrected
  to run only once the scaffold exists (see `backend/README.md`).

## 7. Frontend ↔ backend — NOT VERIFIABLE

No frontend or backend code exists, so no cross-layer check is possible. The
planned route table in `frontend/README.md` §6 is derived from
`contracts/openapi.yaml` paths; conformance must be re-verified once code lands.

## 8. PayPal ↔ implementation — NOT VERIFIABLE

No PayPal calls exist. `docs/DECISIONS.md` open questions 1–5 (exact API product,
vault/off-session support, merchant relationship, webhook events) must be resolved
in Gate 0 before any integration claim can be made.

## 9. Sponsor claims ↔ implementation — NOT APPLICABLE / DOCUMENTED ONLY

- AG Grid: planned for watch/audit tables, not installed — no claim made.
- Render: deployment target named in planning docs; no `render.yaml` — no claim made.
- APIMatic: OpenAPI contract exists and is import-ready; no APIMatic usage present
  or claimed.

## 10. v0.3.0 additions — VERIFIED against implementation

Four endpoints were added with documented reasons (see CHANGELOG):

| Endpoint | Reason | Implementation |
|---|---|---|
| `POST /api/guardian/evaluations` | Stateless playground for judges to probe the deterministic engine without persisting anything | `GuardianController` — pure `GuardianEvaluator`, no I/O |
| `POST /api/watches/{watchId}/offers` | Demo affordance standing in for the discovery adapter while Channel3 is unverified | `PurchaseService.submitOffer` — validates, persists intent, evaluates, reserves |
| `POST /api/purchase-intents/{intentId}/demo-capture` | Lets the demo go end-to-end without a real provider; fenced behind stub mode, always labelled simulated | `PurchaseService.demoCapture` — 409 unless `PAYMENTS_MODE=stub` |
| `GET /api/info` | Public runtime info for the UI settings page | `InfoController` — no secrets |

`DemoCaptureResponse.simulated` is `true` by construction; the backend never
returns it otherwise. The `approve` response now carries the revalidation result
(`OfferEvaluationResponse`) instead of an empty body.

## Standing rule

When code lands, re-run this verification: regenerate the frontend client from
`contracts/openapi.yaml`, diff DTOs against schemas, and update this document.
A contract change without a matching code change (or vice versa) is a defect.
