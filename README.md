# WikiRace

Phase 10: a real-time, 2-4 player Wikipedia race with completed-result PostgreSQL persistence, a reproducible Docker stack and GitHub Actions CI. Active rooms stay in memory; REST commands drive the server-authoritative engine and STOMP broadcasts redacted room snapshots. English Wikipedia content comes from official MediaWiki APIs.

## Requirements

- Java 21 (set JAVA_HOME to a Java 21 installation).
- Node.js 20.19+ or 22.12+; Node 24 is supported.
- npm. The backend includes a Maven wrapper; the first run downloads Maven and dependencies.
- PostgreSQL for normal backend runtime (integration tests use PostgreSQL 16 containers).
- A running Docker-compatible runtime for the backend Testcontainers tests; no manually started test database is needed.

On macOS, select Java 21 with:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Run Locally

### Docker Stack

Install Docker with Compose v2+ (Docker Desktop is sufficient); no host Java, Maven, Node or PostgreSQL is needed to run the stack. From the project root, set a unique database password once and retain it for subsequent starts:

```sh
export DATABASE_PASSWORD=$(openssl rand -hex 24)
docker compose up --build
```

Open **http://localhost:8080**. Keep the same password in your shell environment or ignored root `.env` file; `.env.example` documents the variables without real credentials. A missing password fails clearly before startup. Do not generate a new password for an existing database volume: PostgreSQL initialization variables only apply to a new volume.

The frontend serves the production Vite bundle through non-root Nginx. `/api/` and `/ws` proxy to the backend, and `/room/{code}` refreshes fall back to the SPA. The backend runs Java 21 as a non-root user. PostgreSQL and backend ports remain private to the Compose network; only the frontend is published on loopback. Application containers have read-only filesystems with `/tmp` mounts and dropped capabilities. PostgreSQL stores only completed results in the named `postgres-data` volume; active rooms are still lost on backend restart.

Compose waits for PostgreSQL, backend and frontend health checks in order. PostgreSQL uses `pg_isready`; backend uses the existing `/api/health`; Nginx uses `/healthz`. Backend startup still runs Flyway migrations and Hibernate schema validation. `/api/health` is a liveness endpoint, not a continuing database or Wikimedia readiness check. Nginx access logs omit query strings and never record player-token headers. WebSocket upgrades retain the existing authenticated STOMP protocol and exact-origin validation. See [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order) and [Nginx WebSocket proxying](https://nginx.org/en/docs/http/websocket.html).

To use a different local port:

```sh
FRONTEND_PORT=8090 docker compose up --build
```

Open http://localhost:8090. `PUBLIC_ORIGIN` optionally overrides the absolute browser-facing origin (including scheme and port). It supplies the build-time `VITE_API_BASE_URL` and exact WebSocket origin; use that exact URL in the browser. For example, set `PUBLIC_ORIGIN=http://127.0.0.1:8090` when using that host instead of localhost. Changing port/origin requires rebuilding the frontend. Never put secrets in Vite build variables; they are public browser code. No deployment, TLS or public-host setup is introduced in this phase.

```sh
docker compose up --build --detach --wait --wait-timeout 180
docker compose ps
# Optional stack smoke check; requires host Node 24, does not call Wikipedia:
node scripts/verify-compose.mjs http://localhost:8080
docker compose down
```

`down` preserves completed results. **`docker compose down --volumes` deletes the database**; use only for intentionally disposable stacks. Keep the same Compose project name to reuse its volume. Real Wikipedia remains the normal provider; set your real `WIKIPEDIA_USER_AGENT` before shared/deployed use.

Docker builds use the Maven wrapper and `npm ci` from committed dependency metadata. Multi-stage runtime images contain only the executable backend package or frontend static build, not host build artifacts, source/test trees, dependencies caches or `.env` files. Image tags track Java 21/Node 24/PostgreSQL 16 and maintained Nginx/OS updates; registry/network access is required for a cold build. Tests run separately in CI and via the commands below, not inside production image builds.

### GitHub Actions

`.github/workflows/ci.yml` runs on pushes and pull requests with read-only repository permissions. Backend runs Java 21 Maven tests (including automatically provisioned PostgreSQL Testcontainers and real WebSocket regressions) and packaging. Frontend runs Node 24 `npm ci`, typecheck, tests, production build and offline Playwright checks. A third job builds and health-checks the Compose stack, runs the REST/STOMP/SPA smoke check, and removes its disposable database afterward. Its random database password is generated and masked per run; no repository secret is required. The workflow does not deploy or publish images. Hosted CI execution requires pushing the repository to GitHub; local validation cannot confirm a hosted run.

### Production Deployment

Phase 11 preparation targets Vercel for the static frontend and Railway for one backend plus PostgreSQL. See [exact deployment steps and acceptance checklist](docs/deployment.md). Enable the backend `production` profile with explicit database, exact REST/WebSocket origins and Wikimedia User-Agent variables; set Vercel's public build-time `VITE_API_BASE_URL` to the backend HTTPS origin. The existing client derives the WSS endpoint automatically. No hosted deployment has been verified yet.

### Native Development

From the project root, start the backend in one terminal:

```sh
cd backend
export DATABASE_URL=jdbc:postgresql://localhost:5432/wikirace
export DATABASE_USER=wikirace
export DATABASE_PASSWORD='your local database password'
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The backend listens at http://localhost:8080. `GET /api/health` returns `{"status":"ok"}`.

Create the database and user in your local PostgreSQL installation first. DATABASE_URL must be a JDBC PostgreSQL URL. Flyway applies versioned migrations on startup; Hibernate only validates the schema (`ddl-auto=validate`). Startup requires the configured database, with no production fallback to memory-only persistence. Keep real passwords in your environment, not tracked files.

Real Wikipedia is the default provider. Set `WIKIPEDIA_USER_AGENT` to an identifiable WikiRace agent with your real project URL or contact email, for example `WikiRace/0.0.1 (your actual project URL or contact email)`. Do not use the `test` profile for real gameplay; it selects the deterministic fake provider and makes no external calls. Follow the [Wikimedia User-Agent policy](https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy).

Start the frontend in another terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. The application runs inside the laptop screen. A health check reports backend availability, with retry on failure.

## Multiplayer Gameplay

1. Enter a display name and create a room. Select start/target articles from the autocomplete suggestions, choose unlimited or a time limit of at least three minutes, and save settings.
2. Copy the invite link and open it in a separate browser tab. Enter a different display name and join. The backend still requires 2-4 players; there are no simulated guests, bots or single-player rule changes.
3. Ready both players in their respective tabs, then start from the host tab. Countdown is derived from the server's startsAt; gameplay remains withheld until the server confirms ACTIVE.
4. Click internal article links or use Back. Click counts, location, close status and winner update only from server responses. Uncertain movement delivery exposes Retry Movement using the same UUID, blocking new movement until resolved.
5. At FINISHED, see the winner, elapsed time, clicks and start/target summary. The public results API now exposes all players' ordered visit logs; the existing frontend summary is unchanged and does not yet render those logs.

Each tab keeps its own room code/token in sessionStorage, with a per-room localStorage fallback exposed as Resume Session on invite entry. Storage contains only these credentials, never player identity, gameplay, timer or result state; REST restores the authoritative player identity. Older stored playerId fields are ignored and removed on successful resume. Invite URLs never contain tokens. Refreshing a room tab resumes its own identity. Opening an invite without a tab session permits a separate guest identity. Returning home does not cancel a race. Expired credentials are cleared when resume fails; no replacement identity is silently created.

Normal room synchronization uses native STOMP over `/ws`, not REST polling. Each connection authenticates with `roomCode` and `X-Player-Token` CONNECT headers and may subscribe only to its exact `/topic/rooms/{code}`. Tokens never appear in WebSocket URLs or shared snapshots. Client SEND commands are rejected; all gameplay commands remain REST.

After an authorized subscription receipt, the client fetches current private REST state, including on reconnect. Shared events contain immutable server snapshots with monotonically increasing room versions; the client ignores older/equal versions. Private movement revisions prevent stale location/content, and an older REST public snapshot cannot replace a newer shared snapshot. Article responses must match room, player, title and movement revision. Smooth clock presentation uses server timestamps plus monotonic elapsed time and never transitions race status locally.

The transport negotiates ten-second heartbeats and retries interrupted connections with exponential delays starting at 1.5 seconds, capped at 15 seconds, plus up to 300 ms connection-attempt jitter. Browser offline events immediately disable gameplay and pause the socket; online events reconnect. Gameplay stays disabled until private REST state has resynced. Authorization failures and room-expiry socket closes stop transport retries. Movement is never replayed automatically; an uncertain delivery retains its UUID for an explicit retry and blocks other movement until resolved.

Disconnecting the last socket marks that player offline; another socket for the same identity keeps them online. WAITING slots have a five-minute reconnect grace period, also given to newly created/joined players that have not connected yet. Expired non-host slots are removed and broadcast. An expired host closes the abandoned lobby rather than migrating host identity. COUNTDOWN, ACTIVE and SUDDEN_DEATH retain all racers regardless of disconnects. FINISHED runtime rooms are removed 30 minutes after finish, even with connected clients. Removal revokes tokens and closes room sockets; successfully persisted results remain available.

During ACTIVE/SUDDEN_DEATH, a same-URL History API guard makes native browser Back request the canonical server BACK action when available; it never derives destinations from browser history. Busy/offline/uncertain movement cannot submit another action. The visible Back button remains canonical. Explicit navigation away and finished-state navigation are not intercepted.

Desktop uses the full CSS desk/laptop scene with up to 3px pointer parallax; tablets simplify decorations and mobile prioritizes the screen. Reduced motion disables parallax. Sound effects are short Web Audio tones, unlocked by user interaction, with a mute button and no background music. Articles receive additional DOMPurify sanitization before rendering; backend validation remains authoritative.

## Configuration

| Variable | Default | Use |
| --- | --- | --- |
| JAVA_HOME | Shell's selected JDK | Select Java 21 before running Maven |
| SPRING_PROFILES_ACTIVE | No active profile | Set `dev` to enable development CORS |
| PORT | 8080 | Backend listening port |
| DATABASE_URL | jdbc:postgresql://localhost:5432/wikirace | JDBC PostgreSQL connection URL |
| DATABASE_USER | wikirace | PostgreSQL application user |
| DATABASE_PASSWORD | Empty | Set the configured user's database password in the environment |
| CORS_ALLOWED_ORIGINS | http://localhost:5173,http://127.0.0.1:5173 | Comma-separated exact allowed frontend origins in dev profile |
| WEBSOCKET_ALLOWED_ORIGINS | http://localhost:5173,http://127.0.0.1:5173 | Comma-separated exact native WebSocket origins; empty and wildcard lists are rejected |
| VITE_API_BASE_URL | http://localhost:8080 | Frontend backend base URL, read at dev startup/build |
| ACTION_RECORD_CAPACITY | 2048 | Per-player insertion-ordered cache of accepted movement action records; must be positive |
| MOVEMENT_PER_SECOND | 10 | Positive per-player sliding one-second movement-request limit |
| LOBBY_GRACE | 5m | Positive disconnected/new-player WAITING grace duration |
| FINISHED_RETENTION | 30m | Positive room retention duration measured from finishedAt |
| ROOM_CLEANUP_MILLIS | 10000 | Scheduled stale lobby/finished-room cleanup interval in milliseconds |
| WIKIPEDIA_USER_AGENT | WikiRace/0.0.1 (local development) | Set a real project/contact identifier before shared or deployed use |
| WIKIPEDIA_CONNECT_TIMEOUT | 3s | Positive HTTP connection timeout |
| WIKIPEDIA_LOAD_TIMEOUT | 10s | Positive overall operation budget, including outgoing-link resolution batches |
| WIKIPEDIA_CACHE_SIZE | 500 | Positive maximum entries in each article and alias cache |
| WIKIPEDIA_CACHE_TTL | 30m | Positive expire-after-write duration for both caches |
| WIKIPEDIA_MAX_RESPONSE_BYTES | 8388608 | Positive per-response size limit, enforced during streaming |

Copy `frontend/.env.example` to `frontend/.env.local` for local overrides, or set VITE_API_BASE_URL in the command environment. Local environment files are ignored. No secrets are required or committed.

Vite uses port 5173 and fails if it is occupied. To use another port, set that exact origin in both CORS_ALLOWED_ORIGINS and WEBSOCKET_ALLOWED_ORIGINS when starting the backend and run `npm run dev -- --port 5174`. Development and production-profile CORS allow GET, POST and PATCH without credentialed cookies; production requires explicit approved origins. VITE_API_BASE_URL also determines the native WebSocket base URL (`http` becomes `ws`, `https` becomes `wss`).

## REST Flow

1. POST `/api/rooms` with `{"displayName":"Host"}`. Keep the returned room code and private playerSessionToken.
2. POST `/api/rooms/{code}/join` with a second display name; keep that player's separate token.
3. Search publicly with GET `/api/wiki/search?q=Pet` (up to ten title suggestions). Host PATCH `/api/rooms/{code}/settings` with `{"startArticle":"Pet door","targetArticle":"Consumer IR","unlimited":true,"timeLimitSeconds":null}`. The server resolves casing and redirects, then accepts only existing main-namespace articles.
4. Each player POSTs `/api/rooms/{code}/ready` with `{"ready":true}` and their own `X-Player-Token` header.
5. Host POSTs `/api/rooms/{code}/start` with `{}`. The server sets startsAt three seconds ahead and advances to ACTIVE automatically.
6. GET `/api/rooms/{code}/me/article` with your token to read your server-known current article. The response contains title, sanitized html, sourceUrl, revisionId, roomVersion and movementRevision. Send authenticated POST `/api/rooms/{code}/actions/navigate` with a fresh UUID actionId and destinationArticle from a `data-wiki-title` link in that content. Live links change; do not assume an example route always exists.
7. POST `/api/rooms/{code}/actions/back` with actionId only to return to the server-known previous article. GET `/api/rooms/{code}` reads the redacted room snapshot and the authenticated player's private location.

All existing-room reads and commands require `X-Player-Token`. Settings/readiness commands require WAITING; start requires 2-4 ready players. Successful movement increments clicks once. Timed settings require at least 180 seconds; expiration enters indefinite SUDDEN_DEATH rather than ending the race.

Wikipedia requests use only `https://en.wikipedia.org/w/api.php`: prefixsearch for suggestions, query/info for canonical title and redirect resolution, and parse/text/links/revid for article snapshots. Outgoing links are resolved in batches of at most 50 and filtered by final namespace ID. Disambiguation pages and main-namespace titles containing colons are allowed. HTML uses a Jsoup allowlist; unsafe structures/attributes and images are removed, external/forbidden links become text, and playable anchors use `href="#"` plus server-generated canonical `data-wiki-title`. Section anchors remain local.

Sanitized HTML and canonical edges share one immutable cached snapshot. Each player's current snapshot stays pinned until movement, so cache expiry cannot change their valid edges or close status in place. `/me/article` accepts neither title nor playerId parameters, is available only in ACTIVE/SUDDEN_DEATH/FINISHED, and returns `Cache-Control: no-store`. Clients should discard article responses whose movementRevision no longer matches their private state.

Timeouts, 429/5xx responses, malformed/incomplete data and other upstream failures return `503 WIKIPEDIA_UNAVAILABLE`; missing/non-main articles return `404 ARTICLE_NOT_FOUND`. Failed loads do not apply movement or enter the accepted-action cache. There are no automatic retries or silent fake-provider fallbacks; clients may retry after refreshing state. Large articles or temporary Wikimedia throttling can exceed the configured operation budget.

The fake graph remains in `backend/src/main/java/com/wikirace/wikipedia/FakeArticleProvider.java` for deterministic tests. The `test` Spring profile selects it; normal runtime selects WikipediaArticleProvider.

Retained actionId + identical intent returns DUPLICATE; another intent returns ACTION_ID_CONFLICT. The intent fingerprint consists of movement type and the exact submitted destination string (empty for BACK). Only accepted actions enter the insertion-ordered cache. Records have no time expiration; oldest accepted records are evicted at capacity. After eviction, a replay is a new request subject to current state and link validation, not an indefinitely suppressed duplicate.

After authentication, retained duplicate/conflict checks and race-state validation, each new movement attempt consumes the player's sliding one-second request budget before provider I/O. Ten attempts/second are allowed by default; rejected/upstream-failed attempts count too, but never become accepted action records. Excess attempts return structured HTTP 429 `RATE_LIMITED` without movement or version changes. Retained duplicate retries bypass the budget and remain valid after finish. Budgets are independent across players and disappear with their room/player state.

Room state and tokens are lost on backend restart. Shared REST and STOMP snapshots redact a close player's current article in the payload itself and never include private paths, content or tokens. The authenticated player still sees their own article in REST `me` and `/me/article`. The connected flag reflects live STOMP sessions, not REST requests. Abandoned active/unlimited/Sudden Death rooms remain a resource limitation: they are not expired by changing gameplay rules.

## Completed Results

`GET /api/races/{id}/results` is public and returns completed race metadata and all player results, including ordered visit logs. `room.resultId` becomes the runtime room UUID at FINISHED; it remains valid independently of save success. The response uses DTOs, not runtime objects or JPA entities, and returns `Cache-Control: no-store`.

The server freezes an immutable result under the room lock at winner commit and broadcasts FINISHED before attempting database I/O outside the lock. Completed-result timestamps use PostgreSQL's microsecond precision so retained and persisted DTOs round-trip identically; live race timing is unchanged. A single transaction stores normalized `completed_race`, `player_result` and `visit_step` rows. The visit log includes the start and every accepted Navigate/Back result, preserving repeated articles. Player ordering is retained separately from visit-step ordering. UUID primary/composite keys and an atomic PostgreSQL `ON CONFLICT` aggregate claim make repeated/concurrent saves idempotent. Database constraints enforce participant/winner references and at most one winner row.

Only completed metadata, display names, clicks, final articles, winner flags and visits are stored. No session token or digest, STOMP session, runtime lock, active room snapshot, movement action cache or browser history is persisted. Active races never depend on database writes.

`resultPersistenceStatus` is PENDING/SAVED/FAILED after finish and is broadcast in a separately versioned room update. The original movement response may still contain the earlier PENDING snapshot. A failed save logs only the race ID, leaves FINISHED/winner intact, and retains the frozen result until normal runtime cleanup. There is no durable retry queue or automatic save retry; failed unsaved results can be lost on cleanup/restart.

Results lookup uses a retained finished room first, otherwise PostgreSQL. Known unfinished UUIDs return 409 `RACE_NOT_FINISHED`; unknown results return 404 `RACE_NOT_FOUND`; malformed UUIDs return 400 `INVALID_REQUEST`. If saved-result lookup cannot reach the database, it returns 503 `RESULTS_UNAVAILABLE`; retained results remain readable. Successful saves survive runtime cleanup/backend restart as long as the database is retained. No token/session recovery or active-race persistence is implied.

## Checks

```sh
cd backend
./mvnw test
./mvnw package
```

```sh
cd frontend
npm ci
npm run typecheck
npm test
npm run build
```

Frontend Vitest/Testing Library tests cover validation, timers, movement UUIDs, token headers, sanitization, close redaction, landing/lobby/countdown, confirmed-only movement, duplicate-click prevention, uncertain retry, stale/private response rejection and authenticated STOMP lifecycle/event ordering. Phase 6 adds credential-only restore in all five states, cancelled-resume rejection, native Back guards, capped reconnect/offline listener cleanup and pending-movement exclusion. Backend tests add sliding-window/concurrent rate limits, duplicate bypass, exact grace/retention boundaries, active retention, token revocation and actual expired-room socket closure. Phase 6 verification passed 152 backend tests, 55 frontend tests, typecheck and both builds. No automated test calls live Wikipedia.

Phase 7 verification passed 163 backend tests (including 11 real PostgreSQL integration checks), all 55 frontend tests, typecheck, production build, executable backend package and all 3 existing browser checks. Testcontainers automatically provisions/removes PostgreSQL; Docker must be running. Tests cover Flyway/schema validation, saved race/player fields, ordered Back visits, exact timestamp round trips, concurrent first saves and target arrivals, complete transaction rollback, safe database-write failure, cleanup lookup and application shutdown/restart. Automated tests never call live Wikipedia.

The explicit `test` profile selects FakeArticleProvider and disables database auto-configuration for existing offline gameplay/browser checks; it reports finished results as FAILED rather than claiming a database save. Adding `persistence-test` restores database auto-configuration for PostgreSQL integration checks. Do not use either test profile for normal gameplay.

Offline browser checks use isolated ports 5175/8082 and the preserved backend `test` profile. First build the backend jar with Java 21, then from frontend:

```sh
npx playwright install chromium
npm run test:e2e
```

Alternatively, use installed Google Chrome with `PLAYWRIGHT_CHANNEL=chrome npm run test:e2e`. Export JAVA_HOME for Java 21 first. The tests start/stop their own Vite and fake-provider backend servers; the ports must be free. All three checks cover a two-tab race with WAITING/COUNTDOWN/ACTIVE/FINISHED reload, native browser Back/no-history handling, offline/online recovery, desktop/tablet/mobile framing, keyboard access, reduced motion and Sudden Death reload using deterministic mocked server timestamps. Screenshots/traces are written to ignored test-results/.

Phase 5's four-browser manual check used four isolated Chrome contexts with real REST/STOMP and FakeArticleProvider: A created, B/C/D joined, a fifth player was rejected, settings/readiness/countdown/movement synchronized, close locations were absent from captured payloads, disconnect/resume/refresh preserved state, and simultaneous target clicks produced one winner and results everywhere. A separate room remained isolated; normal synchronization needed no manual refresh or polling. All three existing browser regression checks also passed.

## Project Documents

- [Specification](PROJECT_SPEC.md)
- [Phase plan](PHASE_PLAN.md)
- [Current state](PROJECT_STATE.md)
- [Architecture design](docs/architecture.md)
- [API design](docs/api.md)

Architecture and API documents describe the planned system. Health, room commands, Wikipedia search/content, the playable frontend, room-scoped broadcasts, network hardening and completed PostgreSQL results lookup are implemented. Phase 10 adds Docker orchestration and CI; Phase 11 production configuration is prepared, with hosted verification still pending.
