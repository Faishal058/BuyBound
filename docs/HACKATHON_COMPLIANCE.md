# PayPal AI Hackathon 2026 — Compliance Matrix (v0.5.1)

**Updated:** 2026-11-12. Every Verified item below was exercised against PostgreSQL, Channel3 v1, Groq/OpenAI/Gemini AI, PayPal Sandbox endpoints, and the automated Maven & Vitest verification suites.

| Requirement | Status | Concrete Evidence |
|---|---|---|
| Meaningful PayPal integration | **Verified** | Live OAuth2 (`/v1/oauth2/token`), Vault v3 (`/v3/vault/setup-tokens`, `/v3/vault/payment-tokens`, `DELETE`), Orders v2 (`CAPTURE` & `AUTHORIZE`), Payments v2 (`/capture` & `/void`) in `PayPalPaymentAdapter.java`, and fail-closed webhook signature + replay verification in `WebhookController.java` |
| Autonomous Overnight Poller | **Verified** | `@EnableScheduling` (`BuyBoundApplication.java`) + `OvernightDropScheduler.java` (`@Scheduled`) continuously evaluate active watches without captured intents and record `AUTONOMOUS_OVERNIGHT_POLL_EXECUTED` audit telemetry |
| Meaningful AI functionality | **Verified** | `AiShoppingAgentService.java` powers live NL Watch Parsing (`POST /api/ai/parse-watch`), Untrusted Offer Forensics, and AG Grid AI Filter Copilot (`POST /api/ai/grid-query`) with redacted exception/header handling |
| Channel3 Commerce integration | **Verified** | `Channel3Client.java` & `Channel3SearchService.java` invoke `/v1/search`, `/v1/lookup`, and `/v0/price-tracking/history` enriched with deterministic `resolveMerchantReputation(...)` seller trust scoring |
| Deterministic Wallet Firewall | **Verified** | `GuardianEvaluator.java` deterministically enforces 18 policy/price-band/seller-trust/security checks independently of LLM output, paired with `SpendReservation` atomicity, DNS-rebinding SSRF guards, and masked Vault tokens (`vlt_••••xxxx`) |
| AG Grid Community Showcase | **Verified** | Active across **3 live screens**: `OvernightOfferAuditGrid.tsx` (`/app/offers` & `/app/watches`), `AuditPage.tsx` (`/audit`), and `IntentsTable.tsx` (`WatchDetailPage.tsx`) using `ag-grid-react` & `ag-grid-community` v32.3.9 |
| CISO Security & Session Hygiene | **Verified** | Per-client `HttpOnly` + `X-BuyBound-Session` HMAC sessions (`SessionTokenService`), `SecurityHeadersAndRateLimitFilter` (OWASP headers + token-bucket `429` throttling), expanded `AuditService` DLP redaction, and Render URL auto-normalization |
| Open-source license | **Verified** | Root `BuyBound/LICENSE` (MIT) |
| Reproducible setup & automated tests | **Verified** | `docker compose up --build -d` + `npm run dev`; Render Blueprint (`render.yaml`); `mvn test` and `npm run build && npm test -- --run` |
| Red-Team Security Lab | **Verified** | `POST /api/demo/red-team-attack` exercises poisoned `$899` bundle, `$49` accessory bait, `$29.99/mo` VIP subscription trap, and unapproved merchant hold |
