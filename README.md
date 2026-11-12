# BuyBound

**The Autonomous Overnight AI Shopping Agent & Deterministic PayPal Guardrail Wallet.**  
*AI Proposes. Deterministic Guardian Disposes. PayPal Sandbox Executes.*

BuyBound bridges **Channel3 / Honey-style multi-merchant deal discovery** and **non-custodial PayPal Sandbox execution**. Shoppers define price-drop mandates in plain English before going to sleep, let BuyBound's scheduled agent scan live drops and seller reputations overnight, and wake up to verified purchases settling cleanly inside hard spending caps—with zero risk of prompt injection, scam sellers, or runaway LLM spending.

> **Zero-Trust AI Boundary:** `AiShoppingAgentService` is strictly advisory. Untrusted listing text is wrapped in sanitized `<untrusted_listing>` boundaries, API keys travel strictly in request headers (`x-goog-api-key` / `Authorization`), and **no LLM output or browser payload can bypass `GuardianEvaluator`** or mutate PostgreSQL `SpendReservation` / `Mandate` limits.

---

## The Overnight User Story

1. **Configure Guardrails Before Sleep (`/app/watches`):** A shopper creates a watch—for example, **Nike Air Jordan 1 Retro High OG** (**Target Price:** `$200.00`, **Hard Ceiling Cap:** `$210.00`, **Minimum Seller Rating:** `4.5★`, **0 Red Flags**)—and links PayPal Sandbox Vault once.
2. **Unattended Overnight Discovery (`OvernightDropScheduler` + `Channel3SearchService`):** While the user sleeps, `@Scheduled` polling evaluates `ACTIVE` watches across live Channel3 search results and dynamic drop feeds, enriching every merchant domain with deterministic seller trust telemetry (`resolveMerchantReputation`).
3. **18-Rule Deterministic Guardrail Check (`GuardianEvaluator`):**
   - `Hyped Kicks Liquidator` (`$198.00`, `2.8★`, `14 red flags`) → **`BLOCK`** (`SELLER_NEGATIVE_REVIEW_RED_FLAG`, `SELLER_RATING_TOO_LOW`)
   - `Flight Club` (`$245.00`, `4.9★`, above `$210.00` cap) → **`BLOCK`** (`ITEM_PRICE_CAP_EXCEEDED`)
   - `Nike Direct` (`$204.00`, `4.9★`, `0 red flags`, `"Arriving in 2 Days"`) → **`ALLOW`** (`WITHIN_DROP_GUARDRAIL_BAND`, `WITHIN_POLICY`)
4. **Atomic Spend Lock & PayPal Sandbox Settlement:** `SpendReservation` enforces `UNIQUE (intent_id)` in PostgreSQL, `CheckoutSecurityValidator` verifies SHA-256 `evidenceHash` integrity + DNS-rebinding SSRF safety, and PayPal Sandbox executes the capture using the buyer's server-side Vault token (never exposed back to the browser).
5. **Morning Verification:** The shopper wakes up to **`Autonomous Order Placed — Arriving in 2 Days`**, an interactive **AG Grid Seller & Offer Audit Table**, a printable **Verified PayPal Receipt**, and a redacted **Safety Activity Log**.

---

## CISO-Grade Security & Zero-Trust Architecture

BuyBound implements defense-in-depth across every tier:

- **18-Rule Deterministic `GuardianEvaluator`:** Evaluates mandate status, prompt-injection signatures, exact SKU parity, hidden recurring subscription traps, evidence completeness, currency/quantity caps, merchant allowlists, target vs. hard-ceiling drop price band (`WITHIN_DROP_GUARDRAIL_BAND` / `ITEM_PRICE_CAP_EXCEEDED`), seller rating & negative red flags (`SELLER_RATING_TOO_LOW` / `SELLER_NEGATIVE_REVIEW_RED_FLAG`), ±2% price drift, velocity caps, and weekly spend limits.
- **Scoped Virtual Allowance Ledger & Vault Token Masking:** Atomic PostgreSQL `spend_reservations` prevent concurrent double-spending; raw `paypalVaultToken` secrets never leave the backend (`ActiveMandateTelemetryResponse` and `MandateResponse` return only `vaultConnected` + `maskedVaultToken` such as `vlt_••••90ab`). One-tap Kill Switch immediately revokes mandates, pauses watches, voids held authorizations, and calls PayPal `DELETE /v3/vault/payment-tokens/{id}`.
- **Outbound AI DLP Firewall & Audit Redaction (`AuditService`):** Automatically strips emails, `Bearer` tokens, API key prefixes (`sk-`, `gsk_`, `AIza`, `whsec_`, `A21AA`, `vlt_`), `payerId` values, phone numbers, shipping street addresses, and postal codes from audit logs and upstream AI exception messages. Zero PII (phones, addresses, PayPal payer IDs) is persisted in browser `localStorage`.
- **Fail-Closed PayPal Webhook Verification (`WebhookController`):** Attaches live OAuth2 `Bearer` authorization to `/v1/notifications/verify-webhook-signature`, enforces a strict **5-minute (`300s`) replay window** on `PAYPAL-TRANSMISSION-TIME`, deduplicates by `provider_event_id`, and rejects missing or invalid signatures with HTTP `401 Unauthorized`.
- **Per-Client Session Isolation, OWASP Headers & Rate Limiting:** HMAC-SHA256 `HttpOnly` + `X-BuyBound-Session` tokens isolate concurrent browser sessions (`SessionTokenService`); `SecurityHeadersAndRateLimitFilter` injects `CSP`, `HSTS`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy`, and enforces token-bucket rate limits (`429 Too Many Requests` + `Retry-After: 60`) across auth (`15/min`), AI (`20/min`), and hunt/checkout (`30/min`) routes.
- **DNS-Rebinding SSRF Protection (`CheckoutSecurityValidator`):** Resolves untrusted merchant hostnames via `InetAddress.getAllByName` and blocks loopback, RFC1918, CGNAT (`100.64.0.0/10`), link-local, and cloud metadata (`169.254.169.254`) targets.

---

## Sponsor Stack Deep-Dive

| Sponsor / Platform | Role in BuyBound | Concrete Implementation |
|---|---|---|
| **PayPal Sandbox** | Vaulted autonomous settlement & buyer OAuth | Live OAuth2 client-credentials & OpenID Connect login, Vault v3 (`setup-tokens`, `payment-tokens`, `DELETE`), Orders v2 (`CAPTURE` / `AUTHORIZE`), Payments v2 (`capture` / `void`), `PayPal-Mock-Response` negative testing, and fail-closed Webhook reconciliation. |
| **AG Grid Community v32** | Interactive financial & AI telemetry command center | **3 live grids** powered by `ag-grid-react` & `ag-grid-community` (`^32.3.9`, MIT): (1) `OvernightOfferAuditGrid` on `/app/offers` & `/app/watches` with inline Hold Approve/Decline and NL AI Grid Copilot (`setFilterModel` / `applyColumnState`), (2) `AuditPage` activity log on `/audit`, and (3) `IntentsTable` on `/watches/:id`. |
| **Channel3 Commerce API** | Multi-merchant product & price intelligence | Live `/v1/search`, `/v1/lookup`, and `/v0/price-tracking/history` discovery enriched with `Channel3SearchService.resolveMerchantReputation` for tier-1 vs. risky/unverified merchant verification. |
| **APIMatic / OpenAPI 3** | Contract-first schema & CI enforcement | `contracts/openapi.yaml`, `contracts/guardian-policy.schema.json`, and `contracts/postman_collection.json` validated in CI via `openapi-spec-validator`. |
| **Render Blueprint** | Multi-service cloud hosting | Root `render.yaml` provisions managed PostgreSQL 16 (`buybound-db`), Dockerized Spring Boot 3 API (`buybound-backend`), and Vite static frontend (`buybound-frontend`) with automatic `https://` API normalization and `APP_FRONTEND_URL` callback injection. |

---

## Quick-Start & Judge Verification Guide

### 1. Local Full-Stack Setup (Docker Compose + Vite)
```bash
# 1. Copy environment template and populate Sandbox / API keys (never commit .env)
cp .env.example .env

# 2. Start PostgreSQL 16 + Spring Boot API (http://localhost:8080)
docker compose up --build -d

# 3. Start the React + Vite UI (http://localhost:5173)
cd frontend
npm install
npm run dev
```

### 2. Judge Verification Walkthrough (Local or Hosted Render)
1. Open `http://localhost:5173/` (or your deployed Render `buybound-frontend` URL) and click **Start Shopping** → **Create Account** or **Sign In**.
2. Verify the green **`CISO Security & DLP Shield: Active`** indicator in the top navigation bar and Wallet & Safety page.
3. Visit **Price Alerts (`/app/watches`)**, keep or customize any watch (e.g. Nike Air Jordan `$200 Target / $210 Cap`), and click **`Simulate Price Drop`** or **`Check Stores Now`**.
4. Inspect the **Drop Activity Report** (`Autonomous Order Placed — Arriving in 2 Days`), open **View Verified PayPal Receipt**, and filter the **AG Grid Store Offers** table using plain English (e.g., `"Show approved offers under $210 with 4.5+ seller rating"`).
5. Visit **`/watches/:id`** to inspect the **AG Grid Purchase Intents Table**, and visit **Wallet & Safety (`/app/safety`)** to test the **Scam Shield Red-Team Lab**, **One-Tap Kill Switch**, and **Safety Activity Log (`/audit`)**.

### 3. Automated Verification Commands
- **Backend unit & security tests (`backend/`):**
  ```bash
  mvn test
  ```
- **Frontend type-check, production bundle & Vitest suite (`frontend/`):**
  ```bash
  npm run build && npm test -- --run
  ```

---

## Environment Variables (`.env.example`)

| Variable | Required | Default | Description |
| :--- | :--- | :--- | :--- |
| `PORT` | No | `8080` | HTTP port bound by Spring Boot. |
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/buybound` | PostgreSQL JDBC URL (`postgres://` auto-converted in `backend/Dockerfile`). |
| `CHANNEL3_API_KEY` | Yes | — | Channel3 Commerce `x-api-key`. |
| `AI_PROVIDER` / `AI_API_KEY` | Yes | `groq` | Advisory AI Forensics & Grid Copilot (`groq`, `openai`, `gemini`). |
| `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` | Yes | — | PayPal Developer Sandbox REST credentials. |
| `PAYPAL_WEBHOOK_ID` | Optional | — | PayPal Sandbox Webhook ID (required for `/api/webhooks/paypal`). |
| `APP_FRONTEND_URL` | Yes in Prod | `http://localhost:5173` | Public frontend origin for PayPal OAuth/Vault/Checkout redirects (`https://` auto-normalized). |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173,http://localhost:3000` | Comma-separated allowed browser origins (`APP_FRONTEND_URL` auto-appended). |
| `BUYBOUND_POLLER_ENABLED` | No | `true` | Enables `@Scheduled` `OvernightDropScheduler` background watch polling. |
| `VITE_API_BASE_URL` | Frontend | `http://localhost:8080` | Backend API base URL (`https://` auto-prepended if host-only on Render). |

---

## Contracts & License

- [`contracts/openapi.yaml`](contracts/openapi.yaml) · [`contracts/guardian-policy.schema.json`](contracts/guardian-policy.schema.json) · [`contracts/postman_collection.json`](contracts/postman_collection.json)
- [`docs/PRE_SUBMISSION_CISO_AUDIT.md`](docs/PRE_SUBMISSION_CISO_AUDIT.md) · [`docs/HACKATHON_COMPLIANCE.md`](docs/HACKATHON_COMPLIANCE.md) · [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`LICENSE`](LICENSE) — MIT Open-Source License
