# Architecture — BuyBound

## 1. Design principles
- AI is a discovery assistant, never a payment authority.
- Guardian is deterministic and runs on the server.
- Payment execution is a separate, narrow adapter.
- Persist intent and reservations before external side effects.
- Fail closed on missing, stale, malformed, or contradictory data.
- The first release supports one verified sandbox merchant path, not arbitrary web commerce.

## 2. Logical components
1. **React + TypeScript UI (AG Grid + AG Studio):** Natural language watch creator, Global Trust Bar & Kill Switch, AG Grid Offer Arbitrage Matrix, Guardian Security Telemetry & Hold Approval Queue, Red-Team Attack Lab, Verified PayPal Receipt modal.
2. **Spring Boot API:** Validation, watches, mandates, hunt orchestration, Guardian policy evaluation, and audit query APIs.
3. **Channel3 Catalog Adapter (`com.buybound.catalog`):** Live Channel3 `/v1/search`, `/v1/lookup`, and `/v0/price-tracking/history` calls normalizing multi-merchant offers, baseline prices, verified savings, and affiliate commission rates.
4. **AI Shopping & Forensics Agent (`com.buybound.ai`):** Strictly advisory LLM integration (Groq / OpenAI / Gemini compatible) for NL Watch parsing, untrusted listing forensics (SKU mismatch, prompt injection, subscription traps), and AG Studio NL grid filtering.
5. **Guardian (`com.buybound.guardian`):** Pure deterministic policy evaluation producing `ALLOW`/`HOLD`/`BLOCK`, stable reason codes, and step-by-step `RuleCheckDetail` checklists, paired with atomic `SpendReservation` transactions.
6. **Payment Adapter (`com.buybound.payment`):** Live PayPal Sandbox OAuth2, Vault v3 Setup/Payment tokens, Orders v2 `CAPTURE` / `AUTHORIZE`, and Payments v2 Authorization Capture/Void.
7. **Webhook Handler & Audit Trail:** Verifies provider events and records append-only `AuditEvent` records.
8. **PostgreSQL + Flyway:** Durable watches, versioned mandates, offer snapshots, intents, reservations, decisions, payment attempts, and audit events (`V1`, `V2`, `V3`).

## 3. Deployment shape
For MVP, use one repository with separately runnable processes:
- `api`: Spring Boot HTTP service.
- `worker`: scheduled discovery and purchase orchestration.
- `webhook`: may initially share the API process, but isolate handler logic and scale independently later.
- `frontend`: React app.
- `postgres`: local Docker Compose; managed database for hosted demo.

Do not introduce microservices, Kafka, Redis, Kubernetes, or multiple model agents without measured need. A durable database-backed job/outbox approach is adequate for the first narrow demo.

## 4. Critical trust boundary
The payment adapter must not accept arbitrary amount/merchant/SKU values from the browser or LLM. It receives a purchase intent ID, loads the persisted intent and mandate, checks status and exact Guardian decision, verifies that approved parameters still match, and only then calls the provider.

Guardian ALLOW should be bound to:
- intent ID;
- mandate ID and version;
- merchant ID;
- canonical product/SKU/variant;
- quantity;
- currency and maximum total;
- offer evidence hash/version;
- expiry;
- policy version.

Any relevant change invalidates the decision.

## 5. Purchase state and transaction consistency
Use a durable state machine with explicit allowed transitions. Store an intent before provider calls. Reserve budget in a database transaction with a unique reservation key and constraints. Use provider idempotency keys where supported. After a timeout, query provider status before retrying. Never assume distributed exactly-once execution.

Suggested core entities:
- User
- Watch
- MandateVersion
- OfferSnapshot
- PurchaseIntent
- SpendReservation
- GuardianDecision
- PaymentAttempt
- ProviderWebhookEvent
- AuditEvent

Money is represented with `BigDecimal` and currency code. Store timestamps in UTC.

## 6. AI boundary
LLM may return a strict schema containing candidate product matches, extracted attributes, ranking and explanation. Validate output and all factual attributes against provider/catalogue data. Treat listing text as untrusted input; never place it in a system/developer instruction channel. No LLM call can change policy or directly invoke payment tools.

## 7. External integration gates
### PayPal
Before implementation beyond a mock adapter, verify official current docs and sandbox behavior for:
- API product and account eligibility;
- vault setup and token permissions;
- buyer consent and permitted future/off-session use;
- merchant/platform relationship and onboarding;
- Orders create/capture requirements;
- idempotency behavior;
- webhook event names/signature verification;
- buyer-country and merchant-country availability.

Do not infer arbitrary-merchant capability from a one-merchant sandbox demonstration.

### Channel3
Verify key issuance, terms, endpoint schemas, product coverage, price freshness, rate limits, attribution, and failure behavior. If unavailable, use an explicitly labelled deterministic fixture adapter for UI development; do not call fixtures real catalogue data.

## 8. Security and privacy
- Authenticate and authorize every user-owned resource.
- Apply rate limits and request validation.
- Encrypt secrets and provider tokens at rest using managed key facilities where available.
- Avoid storing raw card details.
- Minimize personal data sent to LLM/catalogue providers.
- Verify webhook signatures before accepting events.
- Use CSRF/CORS protections appropriate to auth design.
- Record security-relevant decisions in append-only audit events.
- Define retention/deletion and incident response before production.

## 9. Scalability path
First optimize shared product lookup, caching, batching, adaptive polling, and durable job scheduling. Measure vendor quota, polling cost, database query latency and reconciliation lag before scaling horizontally. Use DB uniqueness/transactions for correctness; distributed locks are not the source of truth.
