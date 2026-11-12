# Threat Model and Red-Team Cases

## Assets
- User funds and spending authority.
- Provider token references and account credentials.
- Mandates, budgets and revocation state.
- Product identity and offer evidence.
- Purchase-intent state and audit integrity.
- User personal and purchase-history data.

## Trust boundaries
- Browser ↔ backend.
- LLM ↔ application.
- External catalogue/merchant content ↔ application.
- Backend ↔ PayPal.
- PayPal webhook ↔ backend.
- Worker instances ↔ shared database.

## Core threats and mitigations
| Threat | Mitigation | Required test |
|---|---|---|
| Prompt injection in listing | Listing is data only; strict output schema; Guardian independent of LLM | Malicious text cannot change policy or trigger payment |
| Product/variant substitution | Canonical SKU/variant validation and approval binding | Different SKU invalidates ALLOW |
| Price race | Re-quote before execution; enforce max total | Increased price moves intent to HOLD/BLOCK |
| Quantity manipulation | Quantity is mandate-bound and server validated | 10 units cannot replace approved 1 |
| Merchant spoofing | Match trusted merchant identity/ID, not display text alone | Lookalike seller is rejected |
| Concurrent budget overspend | Atomic reservation, DB constraints, transaction isolation | Parallel requests cannot exceed shared budget |
| Duplicate requests | Intent uniqueness and provider idempotency | Retries do not create duplicate logical purchase |
| Ambiguous provider timeout | Reconciliation before retry | No second capture while first is unknown |
| Forged webhook | Signature verification | Invalid signature is rejected |
| Duplicate/out-of-order webhook | Event deduplication and valid transition rules | State does not regress or double-count |
| Revocation race | Recheck mandate at each execution boundary | Revoked mandate blocks new calls |
| Stolen session/account | Strong authentication, authorization, rate limits, re-auth for sensitive changes | Cross-user and replay tests fail |
| Secret leakage | Secret manager/env vars, redaction, no secrets in logs | Automated secret scan passes |
| Catalogue outage/stale data | Freshness TTL, circuit breaker, fail closed | Stale offer cannot be purchased |
| False savings claim | Evidence-backed comparable price and final total | Unsupported savings is omitted or labelled estimated |

## Security invariants
1. No Guardian ALLOW, no payment call.
2. ALLOW applies only to exact, unexpired, persisted intent parameters.
3. Missing information never increases authorization.
4. A revoked mandate cannot authorize new work.
5. A remote payment's status is not inferred from local timeout alone.
6. No model output can alter policy, budget, merchant allowlist, or token reference.
7. All payment-adapter calls carry a correlation ID and intent ID.
8. Secrets and raw payment credentials are never included in model prompts or audit output.

## Demo red-team script
1. Normal offer below threshold → ALLOW → sandbox order.
2. Same offer with price above cap → BLOCK/HOLD; assert payment adapter call count remains zero.
3. Listing text requests policy override → still governed by stored policy.
4. Change product variant after ALLOW → previous decision invalidated.
5. Launch two concurrent purchases against one remaining budget → at most authorized amount reserved.
6. Deliver duplicate webhook → one state update.
7. Simulate timeout after provider request → RECONCILIATION_REQUIRED; no blind retry.
8. Revoke before execution → no new provider call.
9. Break catalogue API → no purchase based on stale data.
10. Return malformed LLM JSON → safe fallback/HOLD.
