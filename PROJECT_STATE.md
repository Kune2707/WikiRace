# Current Phase

Post-MVP — Global Wikipedia Table Rendering (completed; PASS)

# Completed

- Phases 0-3: architecture/API design, Java 21/Spring Boot/Maven foundation, authoritative in-memory game engine and official English MediaWiki integration; FakeArticleProvider preserved for deterministic tests.
- Phases 4-6: responsive CSS desk/laptop frontend, 2-4 player REST/STOMP gameplay, ready/settings/countdown/movement/finish synchronization, close redaction, refresh/resume, network retry guards, rate limiting and room cleanup.
- Phases 7-10: transactional PostgreSQL completed results, frontend polish, security/correctness regressions, non-root Docker/Compose stack and GitHub Actions CI. Product rules remain unchanged.
- Phase 11: public Vercel frontend, single public Railway backend, private PostgreSQL/retained volume; exact production CORS/WSS/JDBC/Flyway configuration and hosted acceptance verified. See docs/deployment.md.
- Phase 11 checks passed: 184 backend tests (11 real PostgreSQL and 6 production transport/CORS), 71 frontend tests, typecheck, backend/frontend builds, 5 offline Playwright checks, Docker checks and hosted CI run 37670362774. No automated tests called live Wikipedia.
- Hosted real-Wikipedia race T6SPS9 finished with one winner at 2026-10-07T19:06:59Z: Computer science -> Computation -> Computer science -> Mathematics, 3 clicks / 134 seconds. Close payload redaction, WSS synchronization and guest refresh passed. Saved result 8fca72d2-7019-449f-bc52-e79e5a1a84a6 was byte-identical after backend restart; application revision 4a1b55b, deployment completion commit 7715a1a.
- Phase 12: production-quality README/demo image, implemented architecture/API reference, reproducible run/test/deployment instructions, engineering tradeoffs/limitations and factual resume/portfolio notes. Documentation-only changes; application/configuration/dependencies unchanged.
- Phase 12 checks passed: nonempty documents, relative links/anchors, balanced fences, shell/JSON snippet syntax, npm command existence, demo JPEG and documentation-only diff; both public frontend and health returned HTTP 200. Application suites/builds were not rerun for this documentation-only phase; prior results above are explicitly historical.
- Post-MVP UI (2026-10-08): explicitly requested full-screen layout replaces the laptop/desk framing, props and parallax. The header holds branding, room, target, timer/sudden death, connection and mute; article/sidebar use stable responsive grids and independent scrolling. Mobile player layout, player badges, article actions, results and fixed lobby action footer are aligned. Backend, rules, API contracts, persistence and dependencies are unchanged.
- Post-MVP checks passed: 73 frontend tests, typecheck, production build, 5 Playwright checks and screenshot review. Landing/lobby/race/results checked at 1440, 1280, 1024, 768, 390 and 320px; countdown, reconnect, close redaction, win/loss, sudden death, long article scrolling, reduced motion and keyboard access remain covered.
- Post-MVP production verification (2026-10-08): UI commit 5f5764eb222eea22fc52cebeae5bdaed4c26c68b pushed to main; Vercel automatically built in 15s and published deployment 4x75UD8qnjHa62sNWB3oAEZz6PfC to the stable public frontend domain. Loaded production assets include index-D5hOGH9A.js and index-Dvg5XFr3.css. Two separate player tabs created/joined ACAL6Z; settings, ready, countdown, movement and finish synchronized over native STOMP. Host moved Computer science -> Computation; guest won Computer science -> Mathematics with 1 click in 56 seconds. Desktop/tablet/mobile checks at 1440, 1280, 1024, 768, 390 and 320px found no horizontal overflow; independent article scrolling and responsive player alignment passed. This is manual hosted verification, not a live-Wikipedia automated test.

# Architecture Decisions

- Post-MVP tables (2026-10-08): both article sanitizers preserve rowspan/colspan, captions, header scope/IDs/relationships, column groups and footers. Recognized table classes are allowlisted; inline layout styles and arbitrary classes remain stripped. Cell blocks are preserved; empty invisible template spacers and empty list markers do not create blank space. Native automatic table layout uses intrinsic widths, never equal columns or global full-width tables; focusable labeled wrappers contain horizontal overflow. Infoboxes stay compact on desktop and in normal mobile flow. Gameplay, navigation validation, DTOs, persistence and dependencies are unchanged.
- Table verification: 37 backend sanitizer/provider tests, 76 frontend tests, typecheck, frontend production build, backend package and 9 Playwright checks passed. Offline official-Wikipedia table excerpts from Richard Burton (1376704316), Academy Award for Best Actor (1378696624) and Summer Olympic Games (1373247608), plus synthetic unknown-class/long-text/list/link/merged-cell cases, were checked at 1440, 1280, 1024, 768, 390 and 320px. Screenshots reviewed for desktop/tablet/mobile; no page/article horizontal overflow, compact tables remain narrow and wide tables scroll via keyboard. Automated tests never contact live Wikipedia. Changes are local only; no commit, push or deployment performed for this request.

- One backend instance; live rooms/tokens in memory with per-room locks. Slow provider I/O, event broadcasting and result saving occur outside room locks; movement commit revalidates player revision. Exactly one valid target arrival wins.
- Backend owns articles, clicks, history/visit log, timers, readiness, close state and winner. REST is the sole command path; native /ws broadcasts full versioned, redacted room snapshots. CONNECT authenticates room/token; subscriptions are room-scoped and client SEND is rejected.
- Each player retains the latest 2,048 accepted action records by default, insertion-ordered/configurable. Same retained ID/intent is DUPLICATE; another intent conflicts. No suppression guarantee after eviction; failed actions are not accepted records.
- ArticleProvider uses official English MediaWiki APIs in normal runtime, fake/mock data in automated tests. Jsoup plus DOMPurify sanitize HTML. Article/alias caches each default to 500 entries/30 minutes; snapshots are pinned per player until movement. Defaults: 3s connection, 10s operation, 8 MiB response limit.
- Browser sessionStorage holds tab credentials; localStorage has a per-room resume fallback, never authoritative identity/gameplay. Ten-second heartbeats, 1.5-15s backoff/jitter and subscription-before-private-resync protect reconnect. Uncertain movement blocks new intents until an explicit same-UUID retry resolves.
- Defaults: 10 new movement attempts/player/second, 5m WAITING grace, 30m FINISHED retention, 10s cleanup. Expired host lobby closes without migration; live race participants are retained.
- Flyway owns normalized completed_race/player_result/visit_step schema; Hibernate validates. Immutable results freeze at winner commit and save transactionally after FINISHED broadcast; separate PENDING/SAVED/FAILED status. Only completed results persist, never tokens, active state, locks or sockets.
- Full-screen CSS/semantic DOM interface, no decorative scene, 3D engine or global state library. This user-authorized post-MVP visual change supersedes the earlier first-person framing; gameplay is unchanged. Result UI shows winner/time/clicks/settings; full ordered paths are in the public results API.
- Production URLs: https://wiki-race-eta.vercel.app and https://wikirace-production-deba.up.railway.app/api/health. Native STOMP: wss://wikirace-production-deba.up.railway.app/ws. Exact allowed origin is the frontend URL; VITE_API_BASE_URL is the backend origin.
- Vercel Hobby and existing Railway trial credit only; no paid plan/add-on/public database endpoint enabled. Railway uses one replica, sleeping off, zero overlap/draining and dashboard settings because new-service legacy Config-as-code is unavailable. Optional incompatible Maven cache mount was removed, with no runtime changes.
- Railway GitHub auto-deploy is disabled with explicit user approval to prevent frontend-only pushes from restarting active rooms. Existing backend deployment 75b42da9-da16-4962-b4c6-63cc04dd0448 (7715a1a) remained active throughout the post-MVP publication; backend configuration/runtime and private PostgreSQL were not redeployed or otherwise modified. Future backend deployment requires explicit action.

# Current API

- Health, public wiki search, create/join/read room, host settings/start, ready, navigate/back, authenticated /me/article and public completed results. Exact DTOs/errors/headers are in docs/api.md.
- GET /api/races/{id}/results exposes completed metadata, winner and all player visit logs; retained result first, PostgreSQL fallback. Known unfinished UUID: 409; missing: 404; malformed: 400; unavailable saved lookup: 503.

# Known Issues

- Live races/tokens do not survive backend restart or support multiple instances. Abandoned active/unlimited/Sudden Death rooms have no timeout, so global live-room memory is not bounded.
- Failed unsaved results disappear on cleanup/restart; no durable retry queue. Full guest names/paths intentionally become public by result UUID. No accounts, matchmaking, leaderboard, spectators or comprehensive anti-abuse system.
- Wikipedia throttling/timeouts remain possible; article images and full-path UI rendering are omitted. Health is liveness, not continuous dependency readiness.
- Railway trial time/credit is finite; paid upgrades require approval. PostgreSQL 18.6 emits a Flyway tested-version warning despite successful migration/validation/restart persistence. Image tags are not immutable digests.
- No performance/adoption/uptime metrics or comprehensive Java vulnerability-database scan claimed. Hosted manual race verification used one computer/network.

# Next Phase

- All phases in PHASE_PLAN.md are complete. Stop; future work requires an explicit user request.
