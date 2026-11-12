# BuyBound Frontend

> **Implementation status: implemented (v0.4.0).** This directory now contains a
> runnable React + TypeScript application built against the backend API contract
> (`../contracts/openapi.yaml` v0.3.0). The UI handles the backend being
> unreachable with explicit error states, never with fake data.

## 1. What this is

The user-facing web app for BuyBound, the guardrailed autonomous buying agent.
Shoppers create product watches with hard spending limits, the backend's Guardian
policy engine evaluates candidate offers, and the UI surfaces decisions, purchase
status, and audit history. The frontend **never executes payments** — all payment
calls happen in the backend adapter.

## 2. Prerequisites

- Node.js 24 (or any current LTS) and npm
- The BuyBound backend running locally (default `http://localhost:8080`), built
  in parallel at `../backend`

## 3. Installation

```bash
cd frontend
npm install
```

## 4. Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend API base URL |

Copy `.env.example` to `.env` to override. No secrets belong in frontend variables.

## 5. Development

```bash
npm run dev      # Vite dev server on http://localhost:5173
```

## 6. Testing

```bash
npm test -- --run   # Vitest, jsdom, React Testing Library (17 tests)
npm run lint        # ESLint (flat config)
```

## 7. Production build

```bash
npm run build    # tsc --noEmit + vite build → dist/
npm run preview  # serve the production build locally
```

## 8. Pages and routes

| Route | Page | Backend APIs used |
|---|---|---|
| `/` | Dashboard — health badge, watches table (AG Grid), new-watch button | `GET /api/health`, `GET /api/watches` |
| `/watches/new` | Create-watch form with validation | `POST /api/watches` |
| `/watches/:id` | Watch header, mandate card, intents table (AG Grid), demo offer form, pause/revoke with confirm dialog | `GET /api/watches/{id}`, `GET …/mandate`, `GET …/purchase-intents`, `POST …/pause`, `POST …/revoke`, `POST …/offers` |
| `/intents/:id` | Status timeline, Guardian decision trace, offer snapshot, approve + demo-capture actions | `GET /api/purchase-intents/{id}`, `GET …/guardian-decision`, `POST …/approve`, `POST …/demo-capture` |
| `/guardian` | Evaluation playground (stateless — nothing is saved) | `POST /api/guardian/evaluations`, `GET /api/watches/{id}/mandate` (load-from-watch) |
| `/audit` | Audit log (AG Grid) with event-type / correlation-ID filters and limit | `GET /api/audit-events` |
| `/settings` | API base URL, backend `/api/info` modes with plain-English explanations | `GET /api/info` |

Every button performs its stated action or is disabled with a reason. Loading,
empty, error, and retry states are implemented on every page. No fake data, no
fake PayPal status, no fabricated metrics anywhere.

## 9. Backend connectivity

Start order: PostgreSQL → backend (`../backend`) → frontend. If the backend is
down, pages show an explicit "API unreachable" error with a retry button — the UI
never renders mock tables or invented transactions.

## 10. Key behaviours to know

- **Demo offer form** (`/watches/:id`) is clearly labelled as demo input
  simulating the discovery adapter — not a real catalogue offer.
- **Demo capture** (`/intents/:id`, only when status is `SPEND_RESERVED`) calls
  the backend stub endpoint and renders the result in a banner that reads
  **SIMULATED — NOT A REAL PAYPAL TRANSACTION**. A `409` shows the backend's
  message as-is.
- **Approve** is only enabled while an intent is `AWAITING_APPROVAL`; approval
  triggers backend revalidation, never bypasses the Guardian.
- **Pause / Revoke** require confirmation; the UI reflects the backend-confirmed
  status afterwards. Revocation blocks new work; in-flight payments are
  reconciled, not promised as cancelled.
- **HashRouter** is used (see `FRONTEND_STRUCTURE.md`) so the app works from
  static file hosting without SPA rewrite rules.

## 11. Tech stack

React 18, TypeScript, Vite 6, React Router 6, Tailwind CSS 3, AG Grid Community
32 (used for exactly two tables: watches, audit log), Vitest + React Testing
Library. Design tokens live in `src/styles/design-tokens.css`.

## 12. Troubleshooting

- **Blank page / API unreachable:** is the backend running on the configured
  `VITE_API_BASE_URL`? Check the dashboard health badge.
- **CORS errors:** the backend must allow the dev-server origin
  (`http://localhost:5173`).
- **Stale types after a contract change:** update `src/services/api/types.ts`
  and `endpoints.ts` to match `../contracts/openapi.yaml`.

## 13. Current limitations

- No authentication UI — the backend auth approach is still undecided.
- No real PayPal UI — by design, purchases execute server-side.
- No AI chat or model UI — the frontend displays Guardian decisions; model
  selection is still open.
- AG Grid is used for the two justified data tables only.
