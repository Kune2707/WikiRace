# Current Phase

Phase 11 — Deployment (preparation verified; hosted deployment pending)

# Completed

- Phase 0 architecture/API design and project control files initialized.
- Phase 1 Java 21 / Spring Boot / Maven backend, health/development CORS and React / TypeScript / Vite / React Router connection screen.
- Phase 2 in-memory rooms, authorization, settings/readiness, countdown/timers/sudden death, authoritative navigation/Back, close redaction, atomic winner and retained-window idempotency.
- Phase 3 official English MediaWiki search, canonical/redirect resolution, namespace filtering, sanitized private article endpoint and canonical gameplay edges implemented.
- Jsoup sanitization, bounded/expiring Caffeine caches, streaming response limit, operation deadlines and structured upstream errors added; FakeArticleProvider preserved for offline tests.
- Backend suite: 114 tests passed with no live Wikipedia calls. Backend executable package, frontend typecheck and production build passed.
- Manual live search and two-player race passed: canonical selection, sanitized content, private access, invalid navigation, close redaction, winner and duplicate handling. Wikimedia 429 produced structured 503 without committing movement.
- Phase 4 responsive HTML/CSS first-person desk/laptop scene, create/join/invite lobby, article autocomplete/settings, readiness, server countdown, article navigation/Back, HUD, close warnings, Sudden Death and finished-state summary implemented.
- Restrained sound cues/mute, keyboard access, reduced-motion support, DOMPurify defense-in-depth sanitization and confirmed-only movement with retained UUID retries added.
- Frontend typecheck, 39 unit/interaction tests, 3 offline Playwright browser checks and production build passed. Backend regressions: 114 tests passed; backend sources unchanged.
- Phase 5 authenticated, room-isolated native Spring WebSocket/STOMP broadcasts added; REST remains the only gameplay command path. Immutable redacted snapshots are captured under room locks and published afterward.
- Ready/settings/countdown/movement/close/sudden-death/finish synchronization, monotonic client ordering, multi-socket presence and disconnect/refresh/resume implemented; normal REST polling removed.
- Verification: 135 backend tests, 44 frontend tests, 3 browser regressions, frontend typecheck and both production builds passed. No live Wikipedia calls in automated tests.
- Four isolated Chrome contexts completed the manual multiplayer race with real REST/STOMP and FakeArticleProvider: fifth-player rejection, payload redaction, room isolation, disconnect/resume/refresh and concurrent single-winner finish passed.
- Phase 6 credential-only session restore, guarded native browser Back, capped exponential reconnect/jitter and offline/online pause/resync added; uncertain movement blocks new intents until explicit same-UUID retry resolves.
- Per-player sliding movement-request limit (10/second), five-minute WAITING grace, abandoned-host lobby expiry and 30-minute FINISHED retention implemented. Cleanup revokes removed identities and closes room sockets without expiring live races.
- Verification: 152 backend tests, 55 frontend tests, frontend typecheck/production build and backend package passed; all 3 browser checks passed with reload/native Back/offline-online regressions. Sudden Death browser reload uses deterministic timestamps; backend restore covers all five real model statuses. No live Wikipedia calls in automated tests.
- Phase 7 Spring Data JPA/PostgreSQL/Flyway completed-result storage and public GET /api/races/{id}/results implemented. Immutable results freeze at winner commit; transactional persistence occurs after unlocking and broadcasting FINISHED.
- Normalized race/player/ordered-visit rows, idempotent concurrent save claim, separate PENDING/SAVED/FAILED status and retained-memory fallback added; active rooms/tokens/WebSocket/runtime state are never persisted.
- Verification: 163 backend tests (11 real PostgreSQL Testcontainers checks), 55 frontend tests, typecheck, frontend production build, backend package and all 3 browser regressions passed. Database rollback/failure, exact timestamp round trips, cleanup lookup, ordered Back visits, concurrent saves/winners and application shutdown/restart verified. No live Wikipedia calls in automated tests.

- Phase 8 refined CSS desk/laptop depth, restrained state transitions, opponent close-edge pulse, distinct win/loss effects and accessible loading/error/disconnected states. All animation/parallax stops under reduced motion; gameplay, backend and dependencies unchanged.
- Verification: 65 frontend tests, typecheck, production build and 4 offline Playwright checks passed. Reviewed desktop/mobile close/loss/error screenshots; verified 320px layout, large article scrolling, reduced motion and parallax cleanup. Unchanged backend package and all 163 regression tests passed.

- Phase 9 audited game/Wikipedia/API/STOMP/persistence correctness and security. Fixed cancelled/overlapping/stale frontend session responses and command-lock ownership; strict JSON now rejects trailing values and duplicate fields with structured 400 errors. No product features or gameplay changes.
- Verification: 178 backend tests including 11 real PostgreSQL persistence checks, 71 frontend tests, typecheck, both builds and 5 offline Playwright checks passed. Added forged-state, hostile-name/external-link, token-log, duplicate-STOMP credential and callback-cleanup regressions. See docs/phase9-audit.md.
- Dependency usage review found no confirmed unused dependencies; manifests unchanged. npm audit reported zero advisories for the installed graph. Maven bytecode warnings were reviewed as starter/transitive/runtime usage, not blindly removed; no Java vulnerability-database scan was run.

- Phase 10 added multi-stage non-root backend/frontend Dockerfiles, Nginx REST/WebSocket proxying and SPA fallback, health-gated PostgreSQL Compose services, environment examples and push/PR GitHub Actions CI. Application sources, gameplay, dependencies and API contracts are unchanged.
- Verification: 178 backend tests (11 PostgreSQL integration checks), 71 frontend tests, typecheck, both builds and 5 offline Playwright checks passed. Both Docker images built; all three services were healthy before/after recreation, with the same retained database/migration. REST/STOMP smoke checks and Chrome production-bundle create/join/refresh/reconnect passed without live Wikipedia calls. Compose configuration and actionlint passed; hosted CI has not run.

# Architecture Decisions

- Phase 11 preparation installed: explicit production profile, environment-driven production REST CORS using the existing mapping, Railway backend manifest (one replica, no sleeping/overlap, existing Dockerfile/health endpoint) and Vercel static Vite build with room-route fallback. Exact deployment steps and hosted acceptance checklist are in docs/deployment.md. Gameplay and dependency manifests are unchanged.
- Publication: full project pushed to https://github.com/Kune2707/WikiRace on main; generated artifacts/private environment files excluded. Local checks passed: 184 backend tests (including 6 production transport/CORS checks and 11 PostgreSQL checks), 71 frontend tests, typecheck, both builds and all 5 offline Playwright checks. Browser tests were rerun after concurrent JAR replacement caused the first attempt to fail; the clean run passed. No automated live Wikipedia calls.
- Only free-tier deployment resources are authorized. Paid resources, plans and add-ons require explicit approval. Vercel/Railway logins are confirmed; Vercel shows Hobby and Railway shows a Trial with $5 credit/30 days remaining at inspection on 2026-10-07. Provisioning is pending manual GitHub connection/repository authorization: Railway lists no accessible repositories, and Vercel has no GitHub import connection. No hosted services or paid resources were created. Production URLs, hosted race/WebSocket verification and persisted-result verification after hosted backend restart remain pending. Phase 11 is not complete; do not begin Phase 12.

- Parallax writes bounded CSS offsets once per animation frame without React state updates; scheduled work and media listeners are cleaned up. Article sanitization memoizes by HTML, not wrapper identity; existing request/WebSocket cleanup remains intact.

- Single backend instance; live rooms in memory with per-room locks. No provider network I/O under room locks.
- Server owns all gameplay and timestamps; room-scoped opaque tokens are stored as digests.
- Accepted-action cache retains 2,048 records/player by default, configurable and insertion-ordered; duplicate suppression ends at eviction or room cleanup.
- Normal runtime uses only the official English MediaWiki Action API; the test profile uses FakeArticleProvider. Automated integration tests mock the HTTP client.
- HTML, revision metadata and canonical outgoing edges form one immutable article snapshot, pinned per player until movement. Private article reads return that snapshot under the room lock.
- Article and alias caches each default to 500 entries/30-minute expiry; connection timeout 3 seconds, overall operation budget 10 seconds, response limit 8 MiB. Environment-configurable.
- Native `/ws` authenticates CONNECT headers, allows only the player's exact room subscription and rejects client SEND. Exact allowed origins are environment-driven; no token URLs or private data broadcasts.
- First-person React hooks consume versioned shared snapshots and REST private state. Subscription receipts precede resync; stale events are ignored, while fresh private REST state can merge without regressing public state. The display clock uses server timestamps plus monotonic elapsed time.
- Browser tabs keep separate room code/token credentials; localStorage retains a per-room resume fallback, never playerId/gameplay. Identity and game state come from REST. Ten-second heartbeats, 1.5-to-15-second exponential reconnect and up to 300 ms attempt jitter; browser offline pauses transport. Gameplay waits for authenticated subscription/private resync.
- Retained duplicates bypass rate limiting. New movement attempts, including rejected/upstream failures, consume a per-player sliding one-second budget before provider I/O; excess returns 429 RATE_LIMITED without gameplay mutation.
- Scheduled cleanup every 10 seconds removes expired non-host WAITING slots, expires an offline host's lobby after five-minute grace (no host migration), and removes FINISHED rooms 30 minutes after finish, closing sockets. Durations/rate/interval are environment-configurable. Initial unconnected players receive the same grace; COUNTDOWN/ACTIVE/SUDDEN_DEATH remain intact.
- Native browser Back uses one same-URL guard during live gameplay and submits only server BACK; unavailable/busy/offline/pending movements are suppressed. Explicit routing remains available and the in-game Back button stays canonical.
- CSS-only scene; no Three.js/WebGL. Frontend additions are icons, HTML sanitization and testing tools only.
- Flyway V1 owns completed_race/player_result/visit_step schema; Hibernate validates, never auto-creates. Result UUID equals runtime room UUID; ordered visits and participant/winner constraints are explicit. Completed-result timestamps use microsecond precision for exact PostgreSQL round trips; live timing is unchanged. No session tokens/digests or runtime state in database.
- FINISHED broadcasts before synchronous bounded-time database I/O outside room locks. One transaction commits all result rows; atomic ON CONFLICT claim prevents duplicate aggregates. Failure preserves winner/runtime result, sets FAILED, logs race ID only; no durable retry queue.
- Public result lookup prefers a retained completed DTO, otherwise reads PostgreSQL transactionally. Saved results survive room cleanup/restart; active sessions do not. Database connection settings are environment-driven. Offline test profile disables DB; persistence-test adds automatically provisioned real PostgreSQL.

- Docker exposes only the frontend on loopback, serving the compiled bundle and proxying `/api/` and native `/ws` to one backend. Backend/frontend run non-root with read-only filesystems; PostgreSQL uses a retained named volume. The required database password stays in the environment or ignored `.env`; browser origin is configured at frontend build time and must match the exact WebSocket allowed origin. CI tests/builds and disposable stack verification do not deploy.

# Current API

- Health; create/join/read rooms; settings; ready/start/navigate/back; public wiki search; token-authenticated /api/rooms/{code}/me/article. See README and docs/api.md.
- Existing REST gameplay APIs are preserved; native `/ws` broadcasts authoritative redacted room events only. Development CORS, WebSocket origins and VITE_API_BASE_URL are configurable.
- Public GET /api/races/{id}/results returns completed metadata, winner and ordered player visit logs; 409 unfinished, 404 unknown, 400 malformed UUID, 503 saved lookup unavailable. Result availability is separate from race state.

# Known Issues

- Hosted GitHub Actions results are not yet verified. Vercel/Railway accounts are signed in, but GitHub connection/repository access requires manual user authorization; no hosted or paid resources have been created. Public HTTPS/WSS gameplay and Railway result persistence remain unverified. Cold builds need registry/dependency access; version-tracking base image tags are not immutable digests.
- Live races do not survive restart; one backend instance only.
- Unlimited/Sudden Death and abandoned active races have no abandonment timeout; retained live rooms can still grow without bound. Changing this gameplay policy requires approval.
- Real Wikipedia may throttle or exceed operation budgets; loads fail without partial movement and may be retried. Configure a real project/contact User-Agent before shared/deployed use.
- Article images are omitted. Abrupt network-loss detection depends on negotiated heartbeat timeouts; room/event state is not durable across restart.
- Full player visit logs are available through the public completed-results API; the unchanged frontend still renders the winner/time/clicks/settings summary only.
- Failed unsaved results disappear after runtime retention/restart; there is no durable retry queue. Normal backend startup requires PostgreSQL. This remains a per-instance, in-memory gameplay limiter.
- Races still require 2-4 real players; two tabs permit a single-browser demonstration without changing backend rules.

# Next Phase

- Finish Phase 11 provider setup and hosted acceptance checks after the user authorizes GitHub access for Vercel and Railway. Do not begin Phase 12.
