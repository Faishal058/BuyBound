# Deployment

## Local (verified path)

Prerequisites: Java 21, Maven 3.9+, Docker Compose, Node.js LTS.

```bash
cp .env.example .env                      # fill local values; never commit
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL 16
cd backend && mvn spring-boot:run         # → http://localhost:8080/api/health
cd frontend && npm ci && npm run dev      # → http://localhost:5173
```

Flyway migrates the schema automatically on backend startup. For a clean slate:
`docker compose -f infra/docker-compose.yml down -v`.

Useful modes:

| Env | Effect |
|---|---|
| `PAYMENTS_MODE=stub` | Enables the clearly-labelled simulated payment adapter + demo-capture endpoint |
| `PAYMENTS_MODE=sandbox` (default) | Real-money path; PayPal adapter refuses until Gate 0 passes |
| `DISCOVERY_MODE=fixture` (default) | Demo offer intake; no Channel3 calls |
| `SPRING_PROFILES_ACTIVE=local` | Debug SQL logging |
| `APP_CORS_ORIGINS` | Comma-separated allowed origins (default `http://localhost:5173`) |

## Docker

`backend/Dockerfile` builds a runnable image:

```bash
cd backend && mvn package -DskipTests
docker build -t buybound-backend .
docker run -p 8080:8080 --env-file ../.env buybound-backend
```

The container still needs a reachable PostgreSQL (`DATABASE_URL`).

## Render (planned, sponsor)

Render is the documented deployment target but no `render.yaml` exists yet and no
deployment has been performed — do not claim otherwise. Planned shape when the
MVP is demo-ready:

- **Web service (backend):** build `mvn -f backend/pom.xml package -DskipTests`,
  start `java -jar backend/target/buybound-0.4.0.jar`; health check `/api/health`.
- **Static site (frontend):** build `npm run build` in `frontend/`, publish `dist/`.
- **PostgreSQL:** Render managed database; `DATABASE_URL` from the dashboard.
- **Secrets:** all credentials as Render environment variables, never in the repo.

## Production readiness gaps (do not deploy publicly until fixed)

Authentication is not implemented (`docs/DECISIONS.md` Q9); PayPal/Channel3/LLM
integrations are pending; no rate limiting beyond container defaults.
