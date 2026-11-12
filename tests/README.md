# Test Strategy

> **Implementation status: not started.** No tests exist yet. This document defines
> what must be tested and how, sourced from `docs/THREAT_MODEL.md` (10-case
> red-team script), `docs/PRD.md` §7–§8 and `docs/PAYMENT_FLOW.md`.

## Priority order

1. **Guardian unit tests** — pure policy evaluator. Required cases (see
   `docs/README_IMPLEMENTATION_NOTES.md`): below cap + allowlisted merchant + fresh
   complete offer → ALLOW; new merchant or uncertain product → HOLD; over cap,
   wrong currency, quantity mismatch, stale offer, revoked mandate → BLOCK/HOLD per
   explicit rules; malformed input → fail closed. Target: 100% pass on required
   deterministic cases (PRD §8).
2. **Spend-reservation concurrency tests** — parallel intents against one remaining
   budget must never over-reserve; uniqueness per intent; transaction isolation.
3. **State-machine tests** — every transition in `docs/PAYMENT_FLOW.md` §2 is valid;
   `BLOCKED` never reaches payment execution; `RECONCILIATION_REQUIRED` cannot be
   blindly retried; duplicates never duplicate purchases.
4. **Failure-mode tests** — the 10 red-team drills in `docs/THREAT_MODEL.md`:
   prompt injection in listing text, variant substitution after ALLOW, price race,
   quantity manipulation, merchant spoofing, concurrent overspend, duplicate
   requests, ambiguous provider timeout, forged/duplicate/out-of-order webhooks,
   revocation race, catalogue outage, malformed LLM output.
5. **Sandbox integration tests** — one real sandbox order created and reconciled;
   mocks clearly labelled and never presented as live.

## Naming and reporting discipline

- Distinguish mock tests from real sandbox tests in names and reports.
- Never fake a payment success, webhook, provider response or transaction ID
  (AGENTS.md rule 6).
- Money-path changes require unit + integration + failure-mode tests (AGENTS.md).

## Running tests (once scaffolds exist)

```bash
# backend
cd backend && mvn verify
# frontend
cd frontend && npm test
```

CI runs `mvn verify` automatically once `backend/pom.xml` exists
(`.github/workflows/ci.yml` → `backend` job). The `contracts` job already runs
today and validates `contracts/openapi.yaml` plus the binding-field completeness
gate.

## Success metrics (PRD §8)

- Guardian policy test pass rate: 100% for required deterministic cases.
- Unauthorized/blocked intent reaching payment adapter: 0 in test suite.
- Duplicate successful purchase from retries: 0 in test suite.
- All non-terminal sandbox intents eventually reconciled or explicitly escalated.
- Demo flow completes repeatedly from a clean state.
