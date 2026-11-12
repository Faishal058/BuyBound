# Payment Flow and State Machine

## 1. Preconditions
- User has accepted a versioned, clearly scoped mandate.
- PayPal Sandbox integration path and account eligibility have been verified.
- Merchant/platform relationship is supported.
- Offer evidence is fresh and complete enough for the configured risk policy.
- No live-money payment is enabled in the MVP.

## 2. State machine
`CREATED → OFFER_VALIDATED → GUARDIAN_EVALUATED`

From Guardian:
- `BLOCKED`: terminal for this intent; no payment API call.
- `HOLD`: `AWAITING_APPROVAL`; approval requires revalidation and fresh Guardian evaluation.
- `ALLOW`: atomically reserve budget and transition to `SPEND_RESERVED`.

Then:
`SPEND_RESERVED → ORDER_CREATE_PENDING → ORDER_CREATED → CAPTURE_PENDING → CAPTURED`

Alternative outcomes:
- `FAILED`: provider-confirmed failure; reservation may be released after reconciliation.
- `RECONCILIATION_REQUIRED`: ambiguous timeout or conflicting provider signals; no blind retry.
- `CANCELLED`: only when cancellation is confirmed and permitted.
- `REFUND_PENDING → REFUNDED`: only if a supported refund operation is actually executed and confirmed.
- `EXPIRED`: mandate or offer expires before execution.

Every transition must be validated and audited. Do not use one generic `FAILED` state to represent unknown outcomes.

## 3. Exact-parameter approval
Guardian approval binds to intent ID, mandate version, merchant, product/SKU/variant, quantity, currency, maximum total, offer evidence version/hash, policy version and expiry. A changed field requires fresh evaluation.

The payment adapter loads these values from persisted state. It does not trust amounts, merchant IDs, or token references supplied by a browser or LLM.

## 4. Spend reservation
Before provider execution, atomically reserve the maximum authorized spend in a database transaction. The reservation operation must:
- verify mandate is active and not revoked;
- verify remaining per-item and period budgets;
- enforce uniqueness per purchase intent;
- prevent concurrent workers from over-reserving;
- persist the reservation and audit event together.

Release or settle reservations only after provider status is sufficiently known. For ambiguous payment outcomes, retain the reservation while reconciling.

## 5. Idempotency and ambiguous outcomes
- Create a stable idempotency key per logical provider operation and reuse it for retries where the provider supports it.
- Persist request metadata and state before the external call.
- Persist response identifiers after the call.
- If timeout occurs after sending a request, query provider state before creating/capturing again.
- A local unique constraint prevents duplicate logical intents but cannot by itself guarantee exactly-once remote execution.

## 6. Webhooks and reconciliation
- Verify signature using the provider-supported verification process before trusting the event.
- Persist raw/minimized event metadata and unique provider event ID.
- Deduplicate repeated events.
- Handle out-of-order events by checking valid state transitions and fetching current provider status when necessary.
- Periodically reconcile non-terminal intents against provider APIs.
- Do not mark an order paid solely because the frontend says so.

## 7. Revocation
Revocation commits a mandate status change and blocks all new jobs/authorizations immediately. Workers recheck active mandate state before reservation and before provider execution. If a provider request is already in flight, query its status; do not promise cancellation unless the provider confirms it.

## 8. Consent and audit
Persist mandate version, timestamp, user-visible terms/version, scope, maximum total, merchant scope, currency, frequency/period caps, expiry, and revocation timestamp. Provide a copy or export. Never store more payment credential data than required by the approved provider integration.

## 9. Failure policy
- Missing/invalid price, SKU, merchant, currency, quantity or timestamp: HOLD/BLOCK.
- Price increase above allowed tolerance or max total: HOLD/BLOCK.
- Guardian unavailable: no payment call.
- LLM unavailable/invalid: deterministic fallback or HOLD.
- Catalogue unavailable: no new purchase based on stale data.
- PayPal timeout: reconcile before retry.
- Webhook unavailable: reconciliation worker continues.
- Database unavailable: no payment execution.
