# Phase 9 Audit

Scope: correctness, security, test coverage and dependency usage in the existing single-instance MVP. No product features, architecture replacement, deployment or CI work.

## Findings Fixed

- Cancelled session restore left frontend controls busy. Cleanup now releases the current attempt without reviving the identity or showing an abort error.
- Overlapping restores could select an older identity; a delayed create/join response could revive a reset session. Attempt generations now reject superseded responses and errors. Restore cannot replace an in-flight command, and its button is disabled while busy. Regression tests reproduced all three failures before the fix and cover command-lock ownership.
- REST accepted trailing JSON values; duplicated record fields could raise an unhandled Jackson conversion error. Strict duplicate detection and trailing-token rejection now return the existing structured `400 INVALID_REQUEST`. Tests reproduce both request shapes and verify no state change.
- Renamed a stale controller test that incorrectly described already-implemented endpoints as future-phase endpoints.

## Coverage Reviewed

| Area | Evidence |
| --- | --- |
| Rooms, host/settings/ready, 2-4 players, countdown, unlimited/sudden death | GameServiceTest, RoomControllerTest |
| Validated navigation, Back/history truncation, clicks and visit order | GameServiceTest, RoomControllerTest, ResultPersistenceTest |
| Close detection/redaction, private own article, immutable single winner | GameServiceTest, RoomEventsTest, StompIntegrationTest, ResultPersistenceTest |
| Concurrent winner/duplicates, rejected and evicted actions | GameServiceTest, NetworkHardeningTest, RoomEventsTest |
| Rate limits, disconnect/reconnect, cleanup and revoked identities | NetworkHardeningTest, RoomCleanupSocketTest, StompIntegrationTest |
| Search, redirects/canonical edges, namespace filtering, caches, timeout/upstream failures | WikipediaArticleProviderTest (mocked HTTP, no live Wikimedia) |
| HTML sanitization, hostile input, external links and safe display names | ArticleSanitizerTest, article.test.ts, Race.test.tsx, RoomControllerTest |
| Forged current article/clicks/winner/history/visits/close state | RoomControllerTest and real REST Playwright check |
| Token scope, structured errors, cache headers, CORS | RoomControllerTest and StompIntegrationTest |
| Exact room subscriptions, client SEND denial, duplicate credentials, forged principals | StompGuardTest, StompIntegrationTest |
| Secret headers removed, error/log token absence | StompGuardTest, RoomControllerTest captured-output test; application logging source review |
| Increasing snapshots, stale event rejection, late callbacks and transport cleanup | RoomEventsTest, useRoom.test.tsx, roomStream.test.ts |
| Transactional completed results, concurrent saves, one winner, ordered Back visits, failure/rollback and restart | 11 real PostgreSQL Testcontainers tests in ResultPersistenceTest |
| Browser race/reload/Back/offline-online, redaction, winner/loss, responsive/reduced-motion/long articles | Offline Playwright checks |

## Dependencies And Cleanup

- Reviewed all frontend direct dependencies against source/config/test imports; all are used. `npm audit --json` reported zero advisories for the installed graph during this run. This is not a guarantee against undisclosed vulnerabilities.
- Reviewed Maven dependency analysis: starter bundles and reflectively loaded PostgreSQL/Flyway dependencies are required despite bytecode-analysis warnings. Transitive Spring/Jackson/JPA/test APIs are supplied by the declared starters. No confirmed unused dependency was found; manifests and lockfiles are unchanged.
- Application source contains no TODO/FIXME placeholders, console debugging, stack-trace printing or raw token logging. DTOs/components are used by implemented endpoints or screens. Test fixtures are not imported by production code. User-owned/generated files were not deleted.
- No Java vulnerability-database scan was run; dependency usage analysis is not such a scan.

## Limits

- This is a scoped source and regression audit, not a penetration test or proof of complete security.
- Active rooms remain in memory on one backend; abandoned live races have no expiry policy. Idempotency is limited to retained action records. No global room-creation/admission limiter was added.
- Failed unsaved results can be lost after cleanup/restart. Results are public by the existing contract and contain no session tokens/runtime state.
- Automated tests use the fake provider or mocked official API responses, never live Wikipedia. Browser checks use the explicit offline test profile; PostgreSQL checks use automatically provisioned real PostgreSQL.
- Existing UI limitation: full visit logs are API-only. No product features were added to close that gap.

## Verification

- Backend `./mvnw -B -ntp package`: 178 tests, zero failures/errors/skips, including all 11 real PostgreSQL tests plus actual WebSocket integration/security/cleanup tests; executable package passed.
- Frontend `npm test`: 71 tests passed. `npm run typecheck` and `npm run build` passed.
- `PLAYWRIGHT_CHANNEL=chrome npm run test:e2e` with Java 21: all five checks passed, including the new real REST forged-state/ambiguous-JSON/foreign-room-token test.
- Normal PostgreSQL-backed packaged runtime starts with Flyway/Hibernate validation; health and preview return 200. Direct malformed JSON smoke checks return structured 400. Existing preview/race processes were not restarted; the hardened preview uses ports 5180/8088.
