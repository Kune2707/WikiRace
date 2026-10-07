# WikiRace API Design

Status: Phase 0 conceptual contract. No endpoints or WebSocket services are implemented. [architecture.md](architecture.md) defines ownership and commit behavior. PROJECT_SPEC.md controls gameplay; phase placement below prevents this design from authorizing early implementation.

## Conventions and Authorization

REST prefix `/api`. JSON requests/responses, UTC ISO-8601 timestamps, UUID identifiers, integer seconds for duration, and nonnegative integer versions/click counts. Room codes are six characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`; clients may trim and uppercase input before sending. Display names follow the specification's 2-20 character permitted-letter/number/punctuation rules.

Room creation/join, health, search and completed-result lookup are public. Every existing-room read or command, including the private article endpoint, requires `X-Player-Token: <opaque-token>` matching that room. Tokens never appear in URLs, shared snapshots, results or logs. Responses containing private state are `Cache-Control: no-store`. Share URLs use `/room/{code}`.

Only documented request fields are accepted. Current article, clicks, history, visit log, timer timestamps, close status and winner are never writable client fields. Invalid JSON, unknown fields, invalid UUIDs or malformed types return 400 INVALID_REQUEST. Tokens authenticate identity; they do not permit host-only operations for non-hosts.

## DTOs

| DTO | Fields |
| --- | --- |
| RaceSettingsDto | startArticle: string or null; targetArticle: string or null; unlimited: boolean; timeLimitSeconds: integer or null |
| PublicPlayerDto | playerId: UUID; displayName: string; host: boolean; ready: boolean; connected: boolean; clickCount: integer; closeToTarget: boolean; currentArticle: string or null |
| RoomSnapshot | roomCode; version; status; hostPlayerId; settings: RaceSettingsDto; players: PublicPlayerDto[]; startsAt; suddenDeathStartedAt; finishedAt; winnerPlayerId; resultId; resultPersistenceStatus |
| PrivatePlayerView | playerId; currentArticle; clickCount; canGoBack: boolean; movementRevision: integer |
| RoomView | serverTime; room: RoomSnapshot; me: PrivatePlayerView |
| SessionResponse | playerId; playerSessionToken; view: RoomView |
| ActionResponse | actionId; outcome: APPLIED or DUPLICATE; appliedVersion; view: RoomView |
| ArticleResponse | roomCode; playerId; roomVersion; movementRevision; title; html; sourceUrl; revisionId: integer or null |
| SearchResponse | results: array of {title: string}; optional plain-text description if later available |
| RaceResultsDto | id; roomCode; startArticle; targetArticle; unlimited; timeLimitSeconds; startedAt; suddenDeathStartedAt; finishedAt; elapsedSeconds; winnerPlayerId; players: PlayerResultDto[] |
| PlayerResultDto | playerId; displayName; clickCount; finalArticle; winner: boolean; visitLog: string[] in action order |

Nullable lifecycle fields are null until applicable. `resultId` becomes the room UUID at FINISHED, independent of database save success. resultPersistenceStatus is null before finish and PENDING/SAVED/FAILED afterward once persistence exists. Prior to Phase 7 it is null and results are available only from retained live state.

Before ACTIVE, article fields in public/private views are null, clicks are zero and canGoBack is false. During gameplay public currentArticle is null whenever closeToTarget is true. The requesting player's own location is available only through `me` and the authenticated article endpoint; `me` contains no close flag. Frontend suppresses self-close alerts even though shared players contain the public warning state. No player's history or visitLog appears in room snapshots during a race. FINISHED result responses intentionally reveal every participating player's complete visit log.

## Endpoints

| Method and path | Request | Success | Phase |
| --- | --- | --- | --- |
| GET /api/health | None | 200 {"status":"ok"} | 1 |
| GET /api/wiki/search?q= | Nonblank query, bounded length; up to 10 suggestions | 200 SearchResponse | 3 |
| POST /api/rooms | {displayName} | 201 SessionResponse; Location points to room API URL | 2 |
| POST /api/rooms/{code}/join | {displayName} | 201 SessionResponse | 2 |
| GET /api/rooms/{code} | Player token | 200 RoomView | 2 |
| PATCH /api/rooms/{code}/settings | Player token; partial settings fields | 200 RoomView | 2 |
| POST /api/rooms/{code}/ready | Player token; {ready: boolean} | 200 RoomView | 2 |
| POST /api/rooms/{code}/start | Player token; empty JSON object | 200 RoomView in COUNTDOWN | 2 |
| POST /api/rooms/{code}/actions/navigate | Player token; {actionId: UUID, destinationArticle: string} | 200 ActionResponse | 2, real provider in 3 |
| POST /api/rooms/{code}/actions/back | Player token; {actionId: UUID} only | 200 ActionResponse | 2 |
| GET /api/rooms/{code}/me/article | Player token; no article input | 200 ArticleResponse | 3 |
| GET /api/races/{id}/results | UUID id, no token | 200 RaceResultsDto for a completed race | 7 |

Creation initially leaves article selection unconfigured; host configures it in the lobby. Joining requires WAITING and capacity below four. An existing token reconnects through authenticated room GET and STOMP rather than joining a second time. Creation/join do not accept a client-selected player ID or token.

Settings PATCH merges supplied fields, then validates the complete candidate. Article selections are canonicalized and must exist; selecting the same final article for start/target is rejected. Explicit null clears an article selection while WAITING; incomplete settings cannot start. `unlimited=true` requires timeLimitSeconds=null; timed settings require integer timeLimitSeconds >= 180. Switching mode requires the matching duration field. Host changes only in WAITING; actual changes reset all readiness. Identical patches leave readiness and version unchanged.

Ready is an explicit boolean, allowed only in WAITING. Start is host-only and requires 2-4 players, valid settings and everybody ready. The server sets startsAt three seconds ahead; a start retry during COUNTDOWN does not restart it and returns INVALID_RACE_STATE. No client timestamp is accepted.

Navigate resolves destination aliases to canonical main-namespace titles and verifies the edge from the server-known location. BACK accepts no destination. A successful move updates clicks/history/visit log exactly once. Clients keep the same UUID when retrying uncertain delivery.

Phase 2 implements an insertion-ordered bounded cache per player containing the most recent 2,048 ACCEPTED movement action records. Capacity is configurable, with 2048 as the default. Each record contains actionId, intent fingerprint and accepted room/version metadata needed for duplicate handling. Entries remain until capacity eviction (oldest accepted record first) or room cleanup; duplicate lookup does not reorder entries and there is no time-based expiration.

Matching retained actionId with the same intent returns DUPLICATE and current state without applying movement again, including after FINISHED. Matching retained actionId with a different intent returns ACTION_ID_CONFLICT. Failed, rejected and transient-upstream actions are not stored as successful action records. Duplicate suppression is guaranteed only while actionId remains retained; there is no infinite replay protection after eviction. An evicted old request is treated as a new request and must pass all current server-authoritative state and navigation validation before any movement can be committed.

The private article endpoint is available in ACTIVE, SUDDEN_DEATH and FINISHED. It never accepts an arbitrary title or playerId. If the player's movementRevision changes during the load, return STATE_CHANGED instead of content for an old position. Clients correlate the response revision with current private state and discard stale responses. Shared room versions alone cannot reliably identify a private article because opponents' updates also increment room version.

Completed results lookup reads a retained finished room first or PostgreSQL after persistence. Never expose partial paths for active races. Return 409 RACE_NOT_FINISHED for a known unfinished race and 404 RACE_NOT_FOUND if no retained/persisted race exists. UUIDs avoid trivially enumerating room-code results but are not an account authorization system. elapsedSeconds derives from finishedAt - startsAt, including overtime.

## Example Movement Intent

```json
{
  "actionId": "b6311565-b67c-46aa-aad6-ff77f5e1c627",
  "destinationArticle": "Graph theory"
}
```

BACK request:

```json
{
  "actionId": "f0998ca7-c710-43d1-aeb7-89155599f7d9"
}
```

Opponent close status (only this player DTO, not a complete room response):

```json
{
  "playerId": "d1df79c0-89a4-4678-9bea-19da938fa8cd",
  "displayName": "Alex",
  "host": false,
  "ready": true,
  "connected": true,
  "clickCount": 7,
  "closeToTarget": true,
  "currentArticle": null
}
```

## Errors

```json
{
  "code": "INVALID_NAVIGATION",
  "message": "The selected article is not linked from your current article.",
  "timestamp": "2026-10-07T05:00:00Z"
}
```

| HTTP | Codes and circumstances |
| --- | --- |
| 400 | INVALID_REQUEST, INVALID_DISPLAY_NAME, INVALID_ROOM_SETTINGS |
| 401 | INVALID_PLAYER_TOKEN: absent, invalid or wrong-room token |
| 403 | NOT_HOST: authenticated non-host attempts host operation |
| 404 | ROOM_NOT_FOUND, ARTICLE_NOT_FOUND, RACE_NOT_FOUND |
| 409 | ROOM_FULL, INVALID_RACE_STATE, PLAYERS_NOT_READY, NO_BACK_HISTORY, RACE_FINISHED, RACE_NOT_FINISHED, STATE_CHANGED, ACTION_ID_CONFLICT |
| 422 | INVALID_NAVIGATION: resolved destination is not a legitimate outgoing main-namespace article |
| 429 | RATE_LIMITED: per-player action spam; include Retry-After |
| 503 | WIKIPEDIA_UNAVAILABLE: upstream timeout, 429/5xx, API error, malformed/incomplete response |

Duplicate successful movement is a 200 safe response rather than DUPLICATE_ACTION error. Error messages do not reveal another player's hidden article or contain raw upstream payloads, stack traces or tokens. An upstream 429 is not a player-spam 429; distinguish it with WIKIPEDIA_UNAVAILABLE. Rejected movements never increment clicks. A time transition detected during a rejected request may still advance room state and broadcast its own version.

## STOMP Contract (Phase 5)

WebSocket handshake path `/ws`; CONNECT headers `roomCode` and `X-Player-Token`; subscription `/topic/rooms/{code}`. The authenticated room/player principal is assigned server-side. Authorize every subscription, reject other-room and wildcard destinations and reject all client SEND messages. Use configured allowed origins and heartbeats. No token in query parameters or events.

RoomEvent fields:

| Field | Meaning |
| --- | --- |
| type | Event enum below |
| roomCode | Authorized room |
| version | Authoritative mutation version, identical to room.version |
| occurredAt | Server time of the state mutation |
| room | Complete immutable redacted RoomSnapshot |

Event types: ROOM_UPDATED, PLAYER_JOINED, PLAYER_READY_CHANGED, SETTINGS_UPDATED, COUNTDOWN_STARTED, RACE_STARTED, PLAYER_MOVED, PLAYER_CLOSE_CHANGED, PLAYER_DISCONNECTED, PLAYER_RECONNECTED, SUDDEN_DEATH_STARTED, RACE_FINISHED. When a movement changes close state, use PLAYER_CLOSE_CHANGED with the full updated room; otherwise PLAYER_MOVED. No private article or path appears in either event. FINISHED indicates clients may fetch completed results when that endpoint is implemented.

One mutation creates one new version. Consumers discard events whose version is <= their last applied version; equal versions are duplicates. REST views participate in the same ordering. Events are complete state snapshots, so missing an intermediate movement event does not require reconstructing patches. Subscription plus snapshot resynchronization handles reconnection; fetch private state/article separately since the broadcast deliberately cannot supply the player's own hidden article.

## Contract Checks for Later Phases

Validate host and token authorization, capacity/readiness/state guards, canonical navigation, BACK history, one click per successful movement, duplicate handling, winner concurrency, timestamps, namespace rejection and HTML sanitization. Assert hidden opponent locations and session tokens are absent from every public payload, not merely hidden in UI. Test room isolation and version ordering for STOMP. Tests use fake article data or mocked Wikimedia HTTP responses.
