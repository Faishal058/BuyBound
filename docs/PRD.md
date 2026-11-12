# Product Requirements Document — BuyBound

**Version:** 0.1 · **Status:** MVP proposal, not production approval

## 1. Product thesis
BuyBound is a proposed Honey-adjacent shopping conversion and trust layer. It aims to close the gap between discovering a price drop and completing a purchase, while keeping spending rules outside the LLM.

## 2. Target user
A deal-driven online shopper who already tracks a specific product and is willing to grant narrowly scoped, revocable purchase authority for a supported merchant.

## 3. Problem
Price alerts still require the shopper to notice the alert, revisit the store, and complete checkout. AI shopping agents introduce additional concerns: incorrect product matching, manipulated listing text, stale prices, quantity errors, and purchases outside the user's intended budget.

## 4. MVP promise
“Set a target price and limits. BuyBound monitors a supported offer, verifies the exact purchase against your mandate, and either blocks it, asks for approval, or executes through a verified PayPal Sandbox path.”

Do not claim universal merchant coverage, production authorization, guaranteed savings, or a funded refund guarantee.

## 5. Core journey
1. User creates a watch.
2. Discovery adapter returns a normalized candidate offer and evidence.
3. Offer validator checks identity, variant, seller, stock, currency, price, and timestamp.
4. Purchase intent is persisted.
5. Guardian evaluates policy and atomically reserves budget if allowed.
6. If `HOLD`, user must approve in the UI; approval does not bypass Guardian re-evaluation.
7. If `ALLOW`, backend payment adapter executes only the exact approved intent.
8. Provider status and webhooks are reconciled.
9. UI displays status, audit trace, and appropriately qualified savings.
10. User can pause a watch or revoke future autonomous buying.

## 6. MVP scope
### Must have
- One curated supported merchant/integration path.
- Product watch creation and pause.
- Target price, maximum total, maximum quantity, currency, and merchant allowlist.
- Deterministic Guardian: `ALLOW`, `HOLD`, `BLOCK`.
- Persisted purchase intents and atomic spend reservations.
- PayPal Sandbox adapter behind an interface.
- Webhook verification and deduplication when configured.
- Reconciliation for ambiguous/non-terminal transactions.
- Audit trail and dashboard.
- Prompt-injection and price-race tests.
- Clear labels for sandbox, mock, estimated, and verified data.

### Should have
- Channel3 discovery adapter, conditional on verified access, documented API behavior, and item coverage.
- AG Grid audit/watchlist view.
- OpenAPI-generated client types.
- One-click pause/revoke for future execution.

### Out of scope
- Any-web universal purchasing.
- Live-money execution.
- SMB procurement, multi-tenant enterprise administration, multiple payment rails.
- Forecasting ML, autonomous negotiation, complex affiliate accounting.
- Automatic refunds without a defined supported refund flow.
- Purchase guarantee advertised as already funded.
- Legal conclusions embedded as hardcoded assumptions.

## 7. User stories and acceptance criteria
### Create a watch
- User can define product identity, target price, maximum total, quantity, currency, and allowed merchant.
- Invalid/ambiguous product identity is rejected or marked for manual review.
- A watch cannot execute without an active, versioned mandate.

### Evaluate an offer
- Every offer has a source, timestamp, merchant identity, product/SKU/variant identity where available, currency, quantity, and price breakdown.
- Stale or incomplete offers cannot return `ALLOW`.
- A change to SKU, variant, merchant, quantity, or total after evaluation invalidates the prior decision.

### Guardian
- Returns `ALLOW`, `HOLD`, or `BLOCK` plus stable reason codes and policy version.
- Checks per-item cap, total cap, merchant allowlist, quantity, currency, freshness, velocity, remaining budget, and product identity.
- LLM output cannot alter stored limits.
- Budget reservations are atomic across concurrent workers.
- Invalid data and service errors fail closed.

### Payment execution
- Only backend can call the payment adapter.
- Adapter accepts a persisted intent ID, not arbitrary client-supplied amount/token details.
- Exact approved parameters are rechecked before execution.
- Provider idempotency is used where supported.
- Ambiguous outcomes are reconciled before retrying.
- Sandbox and mock outcomes are visibly distinguished.

### Revocation
- Revocation immediately blocks new work.
- In-flight provider requests are reconciled and reported accurately; the UI does not promise cancellation where unavailable.

### Audit and savings
- Audit records decision, policy version, input evidence references, reasons, timestamps, and provider correlation IDs, with sensitive values redacted.
- Savings are shown as verified only when comparable reference and actual paid total are supported by evidence; otherwise label as estimated or omit.

## 8. Success metrics
- Guardian policy test pass rate: 100% for required deterministic cases.
- Unauthorized/blocked intent reaching payment adapter: 0 in test suite.
- Duplicate successful purchase from retries: 0 in test suite.
- All non-terminal sandbox intents eventually reconciled or explicitly escalated.
- Demonstration flow completes repeatedly from a clean state.
- Product metrics to validate later: watch activation, alert-to-purchase conversion, incremental purchases, PayPal checkout share, repeat usage, false-block rate, erroneous-purchase rate, and contribution margin.

## 9. Risks and assumptions
- PayPal product eligibility, token permissions, merchant/platform model, and off-session execution are unverified until tested and confirmed.
- Channel3 API access, limits, terms, and product coverage must be verified.
- Sandbox success does not establish production eligibility.
- Merchant affiliate attribution may conflict with existing Honey arrangements.
- Regulatory analysis is jurisdiction- and implementation-specific.
