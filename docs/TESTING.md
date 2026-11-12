# Testing

**Last run:** 2026-10-12. All commands below were executed; results are real.
Environment notes: this machine has no Docker and Maven Central was unreachable
from Maven's HTTP client, so dependencies were fetched with curl into
`/opt/m2repo` and Maven ran with `-o -Dmaven.repo.local=/opt/m2repo`.
PostgreSQL was unavailable, so no live-database boot test was possible.

## Backend (`backend/`)

```bash
cd backend
mvn -o -Dmaven.repo.local=/opt/m2repo test
```

**Result: 36 tests, 0 failures, 0 errors — BUILD SUCCESS.**

| Suite | Tests | Covers |
|---|---|---|
| `GuardianEvaluatorTest` | 20 | All rule groups: caps, currency, quantity, merchant allowlist (HOLD and BLOCK policies), staleness, revoked mandate, velocity, budget, approval threshold, product-identity uncertainty, incomplete evidence, BLOCK-over-HOLD precedence, determinism across runs, null-safety, null-budget skip |
| `GuardianControllerTest` | 3 | HTTP slice with the real evaluator: ALLOW, BLOCK, invalid-request 400 |
| `WatchControllerTest` | 5 | 201 create, 400 on bad currency / missing name, list, 404 |
| `PurchaseServiceTest` | 8 | ALLOW→SPEND_RESERVED + reservation, BLOCK→no reservation, HOLD→AWAITING_APPROVAL, paused-watch conflict, approve revalidation, approve on non-HOLD conflict, demo-capture refused in sandbox mode, simulated capture in stub mode |

```bash
mvn -o -Dmaven.repo.local=/opt/m2repo package -DskipTests
```

**Result: BUILD SUCCESS** — `target/buybound-0.4.0.jar` (48 MB fat jar).

## Frontend (`frontend/`)

```bash
cd frontend
npm install
npm run build     # tsc --noEmit + vite build
npx vitest run
npm run lint
```

**Results:** build succeeds (`dist/` produced); **17/17 tests pass**
(validation 6, API client 4, status badge 7); eslint clean.

## Contract validation

```bash
python3 -c "import yaml; yaml.safe_load(open('contracts/openapi.yaml'))"
python3 -m json.tool contracts/guardian-policy.schema.json
```

Both valid. OpenAPI v0.3.0: 16 paths, 16 schemas; the 4 new endpoints
(`/api/guardian/evaluations`, `/api/watches/{id}/offers`,
`/api/purchase-intents/{id}/demo-capture`, `/api/info`) are implemented in the
backend and consumed by the frontend — verified by inspection of
`frontend/src/services/api/endpoints.ts` against the controllers.

## Secrets scan

No credentials, tokens, private keys, or `.env` files in the archive.
`.env.example` files contain placeholders only. Verified with grep over the
extracted tree.

## Not verified

- **Application boot against PostgreSQL** — no database available in this
  environment (no Docker; `apt` has no postgres package). The JPA mappings,
  Flyway migration (`V1__init.sql`), and datasource config are implemented but
  the startup path is untested. Verify with `infra/docker-compose.yml` before
  any demo.
- **End-to-end browser flow** — frontend and backend were tested separately;
  the integrated journey (create watch → submit offer → approve → demo-capture)
  has not been exercised against a running backend.
- **PayPal Sandbox** — no real provider calls exist by design (Gate 0 open).
