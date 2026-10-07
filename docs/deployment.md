# Phase 11 Deployment

## Status

Deployment preparation is implemented. The authorized repository is https://github.com/Kune2707/WikiRace. No production URLs or authenticated Vercel/Railway projects have been supplied, so no hosted deployment or production race has been verified. Phase 11 is not complete until the acceptance checklist below passes on the actual public URLs. Example domains below are placeholders, not deployed services. Only free-tier resources are authorized; any paid plan, billable resource or add-on requires explicit user approval before provisioning.

## Topology

- Vercel serves the compiled React/Vite frontend.
- One always-running Railway backend serves HTTPS REST and native WSS/STOMP directly.
- Railway PostgreSQL stores completed results on its persistent volume. Active rooms remain in backend memory.

The browser connects directly to Railway, not through Vercel rewrites or Vercel Functions. `VITE_API_BASE_URL=https://YOUR-BACKEND-DOMAIN` yields REST at `/api/...` and the existing client derives `wss://YOUR-BACKEND-DOMAIN/ws`. There is no separate WebSocket environment variable, SockJS endpoint or client command channel. Tokens remain in REST/CONNECT headers, never URLs. Do not append `/api` or `/ws` to the base URL.

Use one region and one backend replica. The manifest disables sleeping and deployment overlap. This is not zero-downtime game-state migration: restart/redeploy loses all live rooms, and old WebSockets can briefly outlive a traffic switch. Redeploy only after races finish, stop the old deployment if needed, and have clients create new rooms afterward. Do not enable multi-region replicas, PR backend environments sharing production traffic, Redis or distributed locks.

## Prerequisites

1. Use the authorized GitHub repository `Kune2707/WikiRace`, production branch `main`, containing this entire monorepo. Exclude ignored credentials and generated files from publication.
2. Sign in to Vercel and Railway and authorize that repository. Confirm Railway billing/resource limits before creating services; do not rely on an indefinitely free database/backend.
3. Choose a stable public frontend origin. Production allowlists must use the exact assigned Vercel production domain or verified custom domain, not a guessed preview URL.
4. Keep passwords in Railway variables. Never place database credentials in Vercel `VITE_*` variables, repository files, shell history commands containing literal secrets, screenshots or reports.

## Railway Setup

1. Create/select a Railway project and production environment. Add PostgreSQL and name its service `Postgres` (or substitute its real service name in every reference below). Confirm a persistent database volume is attached. Keep PostgreSQL on private networking; the browser never connects to it.
2. Add the backend from the GitHub monorepo. Set service Root Directory to `/backend` and Config File to `/backend/railway.json`. The Dockerfile path in that manifest is `Dockerfile`, relative to the backend build context. Leave custom build/start/pre-deploy commands unset: the existing Dockerfile packages the application and starts its JRE runtime. Do not deploy the Compose frontend here.
3. Confirm the resolved deployment settings: Dockerfile builder, one replica, sleeping disabled, `/api/health`, 180-second health timeout, on-failure restart (10 retries), zero overlap/draining. Keep exactly one region; remove any old dashboard multi-region override. See [Railway monorepo paths](https://docs.railway.com/deployments/monorepo) and [configuration reference](https://docs.railway.com/config-as-code/reference).
4. Generate a public backend domain under service Settings / Networking and note its HTTPS origin. Use `PORT=8080` with domain target port 8080 for this Docker image. Railway terminates public TLS; do not change the Java application to listen with an embedded TLS certificate.
5. Configure the variables in the table below before the final production deploy. The frontend origin must be the actual Vercel domain obtained in the next section. Initial provisioning is not acceptance verification until both services have the final URLs.
6. Deploy the backend. Inspect startup for a successful Flyway V1 migration (or an already-current schema), Hibernate validation and server startup. Never set `ddl-auto=create/update`, disable Flyway or use the `test`/`persistence-test` profiles in production.
7. Verify `GET https://YOUR-BACKEND-DOMAIN/api/health` returns HTTP 200 and `{"status":"ok"}`. Health remains public, with no player token. This is application liveness, not ongoing database/Wikipedia readiness; Railway's deployment health check is not continuous monitoring. See [Railway health checks](https://docs.railway.com/deployments/healthchecks).

### Backend Variables

Set these in the Railway backend service, not the Postgres service or Vercel:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `production` |
| `PORT` | `8080` (match domain target port) |
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}?sslmode=require` |
| `DATABASE_USER` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `CORS_ALLOWED_ORIGINS` | `https://YOUR-FRONTEND-DOMAIN` |
| `WEBSOCKET_ALLOWED_ORIGINS` | `https://YOUR-FRONTEND-DOMAIN` |
| `WIKIPEDIA_USER_AGENT` | `WikiRace/0.0.1 (https://YOUR-ACTUAL-PROJECT-URL; YOUR-ACTUAL-CONTACT)` |

Railway resolves `${{...}}` as service variable references; do not enter these expressions as shell substitutions. The PostgreSQL connection must be a **JDBC** URL. Do not copy Railway's `postgres://...` `DATABASE_URL` directly into the Spring datasource. Railway's PostgreSQL template supports TLS; `sslmode=require` encrypts the private connection but does not independently verify the certificate hostname/CA. If a custom database needs verified TLS, supply its CA and use the appropriate PostgreSQL JDBC verified mode rather than disabling TLS. See [Railway PostgreSQL variables](https://docs.railway.com/databases/postgresql) and [variable references](https://docs.railway.com/variables/reference).

The production profile requires explicit database settings, REST/WebSocket origins and a Wikimedia User-Agent, rather than using development defaults. Keep both origin lists identical. Multiple approved frontend domains can be comma-separated exact HTTPS origins without paths or trailing slashes. Do not use `*` or wildcard preview domains. CORS permits GET/POST/PATCH and required preflight headers, including `X-Player-Token`; browser cookies are not used. CORS is not authentication: existing token, room and movement validation still apply.

Existing cache/timeouts/rate/retention defaults remain unchanged. No extra environment variables are needed for Flyway or Hibernate. Obtain service secrets through Railway references, not hard-coded URLs containing passwords.

## Vercel Setup

1. Import the same GitHub repository into a Vercel project. Select the intended production branch, Root Directory `frontend`, framework Vite and Node.js 24.x. `frontend/vercel.json` specifies `npm ci`, `npm run build`, `dist` output and the `/room/:code` SPA rewrite. Do not use the Nginx Dockerfile or add serverless API/WebSocket functions to Vercel.
2. Set `VITE_API_BASE_URL` in the **Production** environment to the exact Railway backend HTTPS origin, with no `/api` suffix. It is a public build-time variable, not a secret. Do not set database variables or session tokens here. Changing it requires a new frontend build/deploy, not just changing a runtime variable. See [Vite deployment on Vercel](https://vercel.com/docs/frameworks/frontend/vite) and [Vercel environment configuration](https://vercel.com/docs/environment-variables).
3. Deploy and record the assigned stable production frontend HTTPS origin. Configure that exact origin in both Railway allowlists and deploy the backend with its production variables. If the backend origin was not available at the first frontend import, set the real URL and redeploy the frontend now. An initial unavailable state during provisioning is not a passing production check.
4. Open the stable production domain and directly open/reload `/room/ABC234`. The latter should load the application entry route (a nonexistent room can show its normal join/error state), not a Vercel 404. Existing static assets must load normally.
5. Leave Preview deployments unconfigured or use a separately approved exact preview origin and isolated backend/database. Do not broaden production CORS to every `*.vercel.app` preview. Ensure the chosen demo URL is accessible to ordinary visitors, not only authenticated Vercel team members; review deployment protection settings for that domain.

## Production Acceptance Checklist

Perform this manually against real English Wikipedia; automated tests remain offline. A production smoke script or a healthy process alone does not prove a completed race or durable results.

1. Visit the deployed frontend in two isolated browser contexts, preferably a second device/network too. Verify HTTPS, Backend connected, no mixed-content/CORS errors, and the loaded bundle using the Railway API origin.
2. Create a room as host; open its invite in the second context and join as another player. Verify native `wss://YOUR-BACKEND-DOMAIN/ws` upgrades with HTTP 101, authenticates and reports Connected. Do not export CONNECT frames containing session tokens.
3. Select canonical articles using live official Wikipedia autocomplete. For a short reproducible *manual* race, choose `Computer science` as start and `Mathematics` as target if the current article contains that legitimate internal link; otherwise choose a target actually linked from the current start. Wikipedia content may change. Never bypass server validation or switch production to FakeArticleProvider.
4. Save settings, ready both players and start. Both clients must show the same server countdown and ACTIVE state. Verify readable article content, server click counts, synchronized movement and an eligible Back movement. A rejected move must not increment clicks.
5. When a player is close, verify the opponent shows CLOSE without their article location. Inspect shared REST/STOMP payloads without saving tokens: the close player's currentArticle must be null in the public room payload, while their private article is still readable.
6. Refresh one player during the race and verify same-identity reconnect/private resync. Disconnect/reconnect a context and verify presence and synchronization. REST remains the command path; client SEND must not be introduced.
7. Complete the legitimate target move. Exactly one winner must appear in both clients and further movement must be rejected. Wait until room `resultPersistenceStatus` is SAVED; a FINISHED response alone is insufficient.
8. Record the nonsecret room/result UUID and fetch `GET https://YOUR-BACKEND-DOMAIN/api/races/RESULT_UUID/results` without a player token. Confirm winner, participant clicks, article settings and ordered visit logs. Do not record session credentials.
9. With no active races, restart/redeploy **only the backend**, keeping the same Postgres service/volume. Fetch that same result UUID and compare its completed data. The old active/session room may be gone; the completed result must survive. This proves a database lookup rather than the retained-memory fallback.
10. Record actual frontend/backend URLs, deployment commit, timestamp, nonsecret result UUID and checklist outcomes in PROJECT_STATE.md. Only then mark Phase 11 complete/PASS. Do not begin Phase 12.

## Failure Handling

- Backend fails startup: inspect resolved JDBC references, Postgres readiness/TLS and required production variables. Do not add memory-only fallbacks or disable schema validation.
- REST works with curl but not the browser: verify production profile and exact CORS origin; inspect OPTIONS requests for content-type/X-Player-Token. Rebuild if VITE_API_BASE_URL is stale.
- WebSocket fails: verify `wss`, `/ws`, the exact frontend origin, the Railway backend domain and a single live instance. Do not proxy through Vercel or put tokens in query strings.
- Wikimedia 429/timeout: retain the authoritative position, retry manually later and check the real contact User-Agent. Do not claim deployment verified by swapping to the fake provider.
- Results are FAILED: inspect database connectivity and migration logs; do not claim persistence from an in-memory result response.
- Redeploy/reset: choose a maintenance window. Never delete the Postgres service/volume to fix a backend deployment. Review backup/restore support and resource budgets before sharing the demo. Roll back the application only with schema compatibility; Flyway migrations are forward-only.

## Local Checks

Run backend Maven tests/package (including PostgreSQL Testcontainers), frontend tests/typecheck/build and offline Playwright before deployment. Production-profile tests cover HTTPS-origin CORS/preflight and native STOMP origin/authentication using FakeArticleProvider without live Wikipedia or provider accounts. Docker verification checks packaging/startup independently of Vercel/Railway provisioning. These checks do not substitute for the hosted acceptance checklist.
