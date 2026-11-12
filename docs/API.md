# API Reference (v0.5.0 — Live Channel3, AI Forensics, Guardian & PayPal Sandbox)

Base URL: `http://localhost:8080`. Machine-readable baseline contract: `contracts/openapi.yaml` v0.3.0. Errors always use `{"timestamp","status","error","message","path"}`.

## Health & info

```http
GET /api/health            → 200 {"status":"UP"}
GET /api/info              → 200 {"name":"buybound","version":"0.5.0","paymentsMode":"sandbox",
                                 "discoveryMode":"channel3","aiProvider":"groq","aiModel":"openai/gpt-oss-120b",
                                 "channel3Configured":true,"aiConfigured":true}
```

## Channel3 Catalog & AI Shopping Agent

```http
POST /api/ai/parse-watch         {"prompt":"Buy Sony WH-1000XM5 headphones under $260..."}
  → 200 {"title","query","targetPrice","maxItemPrice","weeklySpendLimit","allowedMerchants","aiExplanation"}

POST /api/ai/grid-query          {"query":"Show blocked prompt injection attacks","view":"offers"}
  → 200 {"filterModel":{...},"sortModel":[...],"groupColumns":[],"insightSummary":"..."}

GET  /api/catalog/search?q=...&limit=8
POST /api/catalog/lookup         {"url":"https://www.bestbuy.com/..."}
  → 200 {"query","productTitle","canonicalProductId","baselineMarketPrice","offers":[...]}
```

## Watches, Mandates & PayPal Vault

```http
POST   /api/watches
GET    /api/watches
GET    /api/watches/{id}
GET    /api/watches/{id}/mandate
GET    /api/watches/{id}/purchase-intents
POST   /api/watches/{id}/hunt              → Channel3 Scan → AI Forensics → Guardian → PayPal Sandbox
POST   /api/watches/{id}/pause
POST   /api/watches/{id}/revoke

GET    /api/mandates/active                → Active mandate budget, PayPal Vault status, KPI totals
POST   /api/mandates/{id}/connect-paypal   → Creates PayPal Sandbox Vault Setup Token + approvalUrl
POST   /api/mandates/{id}/complete-paypal  → {"setupTokenId":"..."} exchanges for PayPal Vault Payment Token
POST   /api/mandates/{id}/kill-switch      → Revokes active mandates, freezes watches, deletes Vault tokens
```

## Guardian & Red-Team Security Lab

```http
POST /api/guardian/evaluations
  → 200 {"decision":"ALLOW"|"HOLD"|"BLOCK","reasonCodes":[...],"ruleChecks":[...],"evaluatedAt","expiresAt"}

POST /api/demo/red-team-attack             {"watchId":"...","attackType":"POISONED_BUNDLE_899"}
  → Runs POISONED_BUNDLE_899 | ACCESSORY_EARPAD_BAIT | HIDDEN_VIP_SUBSCRIPTION | UNAPPROVED_SHADY_SHOP
    live through AI Forensics + Deterministic Guardian + PayPal Sandbox
```

## Purchase Intents, Offers Matrix & PayPal Holds

```http
GET  /api/offers                           → All scanned Channel3 / Red-Team offers for AG Grid View A
GET  /api/purchase-intents                 → All purchase intents for AG Grid View B
POST /api/watches/{id}/offers              → Manual offer evaluation
GET  /api/purchase-intents/{id}
GET  /api/purchase-intents/{id}/guardian-decision
POST /api/purchase-intents/{id}/approve
POST /api/purchases/{id}/approve-hold      → Captures PayPal authorization / completes order capture
POST /api/purchases/{id}/reject-hold       → Voids PayPal authorization & releases reservation
POST /api/purchases/{id}/sync-paypal       → Syncs & captures buyer-approved PayPal Sandbox order
```

## Audit & Webhooks

```http
GET  /api/audit-events?limit=50&eventType=&correlationId=
POST /api/webhooks/paypal
```
