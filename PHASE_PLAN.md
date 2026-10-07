# WikiRace Implementation Plan

This file defines the implementation order for WikiRace.

PROJECT_SPEC.md is the authoritative source for product behavior, gameplay rules, architecture constraints, UI/UX requirements, and security requirements.

AGENTS.md defines permanent working rules.

PROJECT_STATE.md tracks the current implementation state.

## Global Phase Rules

For every phase:

1. Read:
   - AGENTS.md
   - PROJECT_SPEC.md
   - PHASE_PLAN.md
   - PROJECT_STATE.md

2. Inspect the existing repository before making changes.

3. Implement only the explicitly requested phase.

4. Never implement future-phase features early.

5. Preserve working functionality from completed phases.

6. Do not silently change gameplay rules or architecture decisions.

7. Prefer simple, maintainable implementations over unnecessary abstractions.

8. Run relevant tests and build checks before declaring the phase complete.

9. Fix issues caused by the current phase.

10. Update PROJECT_STATE.md.

11. Stop.

At the end of every implementation phase, report:

PHASE STATUS: PASS or FAIL

Completed:
- ...

Tests:
- ...

Build:
- ...

Files changed:
- ...

Known limitations:
- ...

Manual checks:
- ...

PROJECT_STATE.md updated:
- yes/no

---

# Phase 0 — Architecture Design

## Goal

Design the concrete architecture before application code exists.

Do not implement gameplay, backend, frontend, database, or external API functionality in this phase.

## Deliverables

Create:

- docs/architecture.md
- docs/api.md

Update:

- PROJECT_STATE.md

## Backend Architecture

Define a concrete package structure similar to:

backend/src/main/java/.../

- config
- controller
- dto
- exception
- game
  - model
  - service
  - runtime
- wikipedia
- websocket
- persistence
- common

Do not create unnecessary interfaces.

Explain responsibilities for:

- REST controllers
- game services
- runtime room state
- DTO mapping
- Wikipedia integration
- WebSocket broadcasting
- error handling
- persistence
- scheduled tasks / cleanup

## Runtime Domain Model

Design:

- RaceRoom
- PlayerRaceState
- RaceSettings
- RaceStatus
- NavigationHistory
- MoveAction
- MoveType

RaceStatus:

- WAITING
- COUNTDOWN
- ACTIVE
- SUDDEN_DEATH
- FINISHED

MoveType:

- NAVIGATE
- BACK

Document invariants.

## State Ownership

Explicitly document which state is authoritative on the backend.

Frontend must never be authoritative for:

- current article
- click count
- path
- timer
- winner
- close-to-target state
- player ready state
- race status

## REST API Contract

Design conceptual endpoints.

At minimum:

GET /api/health

GET /api/wiki/search?q=

POST /api/rooms

POST /api/rooms/{code}/join

GET /api/rooms/{code}

PATCH /api/rooms/{code}/settings

POST /api/rooms/{code}/ready

POST /api/rooms/{code}/start

POST /api/rooms/{code}/actions/navigate

POST /api/rooms/{code}/actions/back

GET /api/rooms/{code}/me/article

GET /api/races/{id}/results

Define conceptual request/response DTOs.

Do not implement endpoints yet.

## WebSocket Architecture

Define:

- connection endpoint
- subscription destination
- room event model
- event versioning
- token validation strategy
- reconnect strategy
- event types

Include events conceptually equivalent to:

- ROOM_UPDATED
- PLAYER_JOINED
- PLAYER_READY_CHANGED
- SETTINGS_UPDATED
- COUNTDOWN_STARTED
- RACE_STARTED
- PLAYER_MOVED
- PLAYER_CLOSE_CHANGED
- PLAYER_DISCONNECTED
- PLAYER_RECONNECTED
- SUDDEN_DEATH_STARTED
- RACE_FINISHED

## Wikipedia Integration Design

Choose official Wikimedia / MediaWiki API mechanisms for:

- search
- article lookup
- redirects
- article content
- outgoing links

Document:

- canonical article titles
- namespace filtering
- disambiguation handling
- HTML sanitization
- external link handling
- caching
- timeouts
- upstream failures

## Concurrency Strategy

Design a per-room synchronization approach.

Document handling for:

- simultaneous moves
- simultaneous target arrivals
- exactly one winner
- duplicate action IDs
- state version increments
- avoiding long network calls while holding room locks
- rechecking state before committing changes

## Reconnect Strategy

Design:

- opaque player session token
- X-Player-Token header
- localStorage use
- reconnect after refresh
- disconnected player behavior
- lobby grace period
- active-race retention

## Frontend Architecture

Define a structure similar to:

frontend/src/

- api
- components
  - environment
  - laptop
  - lobby
  - race
  - results
- hooks
- pages
- realtime
- state
- styles
- types
- utils

Explain state ownership.

## First-Person UI Structure

Document components such as:

GameEnvironment
  RoomBackground
  DeskScene
    DeskLamp
    CoffeeCup
    Notebook
    Laptop
      LaptopDisplay
        Application UI

Document desktop, tablet, and mobile behavior.

## Persistence Design

Design completed-race persistence.

Do not implement database yet.

Document distinction:

- active room state = memory
- completed race history = PostgreSQL

## Acceptance Criteria

Phase 0 passes only if:

- docs/architecture.md exists
- docs/api.md exists
- backend architecture is defined
- frontend architecture is defined
- API contract is defined
- WebSocket design is defined
- concurrency strategy is defined
- reconnect strategy is defined
- Wikipedia strategy is defined
- persistence strategy is defined
- no real application functionality was implemented
- PROJECT_STATE.md is updated

STOP after Phase 0.

---

# Phase 1 — Project Foundation

## Goal

Create the backend and frontend foundations only.

Do not implement gameplay.

## Backend

Create a Spring Boot project using:

- Java 21
- Maven
- Spring Web
- Spring Validation
- Spring Boot Test

Implement:

GET /api/health

Response:

{
  "status": "ok"
}

Add development CORS configuration.

Do not add PostgreSQL, WebSockets, or Wikipedia integration unless required only as harmless project configuration.

## Frontend

Create:

- React
- TypeScript
- Vite
- React Router

Create a minimal app that calls:

GET /api/health

Show a clear:

Backend connected

or backend unavailable state.

Do not build the first-person WikiRace UI yet.

## Environment Configuration

Frontend:

- VITE_API_BASE_URL

Backend:

- environment-driven configuration

Do not commit secrets.

## Tests

Backend:

- health endpoint test

Frontend:

- TypeScript check
- production build

## Documentation

Add minimal local run instructions to README.

## Acceptance Criteria

- backend starts
- frontend starts
- frontend can call backend
- backend tests pass
- frontend typecheck passes
- frontend build passes
- no gameplay implementation exists
- PROJECT_STATE.md updated

STOP.

---

# Phase 2 — Core Game Engine with Fake Article Graph

## Goal

Build the full gameplay engine without real Wikipedia, WebSockets, or PostgreSQL.

Use in-memory runtime state.

## Fake Article Provider

Create a deterministic fake article graph.

Example nodes:

Computer Science
Algorithm
Mathematics
Graph Theory
Physics
Quantum Mechanics
Network
Internet
World Wide Web

Use enough links to test real navigation behavior.

The game engine must depend on an article/link provider abstraction so FakeArticleProvider can later be replaced by real Wikipedia integration.

## Room Creation

Implement room creation.

Create:

- UUID room ID
- 6-character room code
- host player
- player session token
- WAITING status

## Joining

Implement room join.

Rules:

- room exists
- room is WAITING
- maximum 4 players
- minimum 2 required to start
- display name validation

## Settings

Host may configure:

- start article
- target article
- timer
- unlimited mode

Only during WAITING.

Changing any setting resets all ready states.

Validate:

- start != target
- timed duration >= 3 minutes

## Ready System

Every player must become ready.

Host may start only when all players are ready.

## Countdown

Implement:

WAITING -> COUNTDOWN -> ACTIVE

Use authoritative server startsAt timestamp.

Countdown duration:

3 seconds.

## Navigation

Implement server-authoritative NAVIGATE.

Request includes:

- actionId
- destinationArticle

Server determines:

- player identity from token
- current article
- outgoing links
- valid/invalid navigation

Successful movement:

- increment click count once
- update browser-style navigation history
- update current article
- append new article to visit log
- update close state
- check winner

## Back

Implement server-authoritative BACK.

Request contains:

- actionId only

Server determines previous article.

Successful BACK:

- move history cursor backward
- increment click count once
- append resulting article to visit log
- update current article
- update close state

Invalid Back must not increment click count.

If a player navigates after going back:

truncate forward history.

## Navigation History

Maintain:

- ordered history
- cursor

Example:

A -> B -> C

Back:

A -> B -> C
     cursor B

Then navigate D:

A -> B -> D

Forward branch is removed.

## Visit Log

Maintain actual visited sequence.

Example:

A
B
C
B
D

Starting article is first entry.

## Click Count

Increment exactly once for successful:

- NAVIGATE
- BACK

Do not increment for:

- invalid move
- invalid Back
- duplicate action
- retry
- movement before active state
- movement after finish

## Idempotency

Use UUID actionId.

A repeated actionId must never execute twice.

Maintain a bounded recent-action record per player.

## Close-to-Target

A player is close when:

target ∈ outgoingLinks(currentArticle)

Set closeToTarget.

Public room DTO must redact that player's current article from opponents.

Moving away clears close status.

## Winner

If valid destination == target:

- assign winner once
- mark FINISHED
- reject further movement

Exactly one winner must exist.

## Timers

Timed race:

ACTIVE until timer expires.

If no winner:

ACTIVE -> SUDDEN_DEATH

Sudden Death continues indefinitely until first valid winner.

Unlimited race:

timer counts upward and never expires.

## Room Version

Increment authoritative room version after every state mutation.

## Tests

At minimum test:

- room creation
- room code generation
- join
- room full
- invalid display name
- host settings change
- non-host settings rejection
- settings reset ready states
- ready toggle
- start with one player rejected
- start when not all ready rejected
- valid start
- countdown
- ACTIVE transition
- timed race
- unlimited race
- sudden death
- valid navigation
- invalid navigation
- click count
- visit log
- valid Back
- Back increments click
- invalid Back
- navigation after Back truncates forward history
- close detection
- article redaction
- moving away clears close
- winner
- movement after finish rejected
- duplicate action
- duplicate action does not increment click
- simultaneous target attempts
- exactly one winner
- room version progression

## Acceptance Criteria

- game engine works entirely with FakeArticleProvider
- core game service is independent of HTTP details
- all tests pass
- no real Wikipedia integration yet
- no WebSocket yet
- no database yet
- PROJECT_STATE.md updated

STOP.

---

# Phase 3 — Real Wikipedia Integration

## Goal

Replace the fake provider in real runtime with real English Wikipedia data.

Keep fake provider for tests.

## Official API

Use official Wikimedia / MediaWiki APIs.

Do not scrape arbitrary rendered browser pages when API data is available.

## Search

Implement:

GET /api/wiki/search?q=

Return small normalized results such as:

- title
- optional snippet/description

## Canonicalization

Resolve and normalize:

- redirects
- underscores/spaces
- canonical title casing where applicable

Game state should use canonical titles.

## Namespace Rules

Allow main article namespace.

Reject gameplay navigation into namespaces such as:

- File
- Category
- Help
- Talk
- Special
- User
- Template
- Portal

Disambiguation pages are allowed.

## Article Content

Provide readable WikiRace article content.

Prefer sanitized HTML.

Use server-side sanitization such as Jsoup.

Allow only safe structures required for reading.

Remove:

- script
- style
- iframe
- form
- input
- object
- embed
- event-handler attributes
- unsafe URLs

Normalize legitimate Wikipedia article links into application-controlled internal links.

Example metadata:

data-wiki-title="Graph theory"

## Article Endpoint

Implement authenticated endpoint for the player's current article:

GET /api/rooms/{code}/me/article

Server determines current article from player token.

Do not allow arbitrary article state impersonation.

## Navigation Validation

Real gameplay validates:

destination ∈ outgoingLinks(serverKnownCurrentArticle)

## Close Detection

Real close state uses:

target ∈ outgoingLinks(currentArticle)

## Cache

Add a bounded cache using Caffeine or equivalent.

Cache:

- canonical title
- sanitized article content
- outgoing links

Use expiration and maximum size.

Do not use unbounded maps.

## External API Safety

Implement:

- timeout
- upstream 429 handling
- 5xx handling
- missing article handling
- malformed response handling
- identifiable Wikimedia User-Agent

## Tests

Tests must not call real Wikipedia.

Mock the external HTTP boundary.

Test:

- search parsing
- canonicalization
- redirect handling
- namespace filtering
- outgoing links
- sanitization
- unsafe HTML removal
- valid navigation
- invalid navigation
- close detection
- timeout
- upstream failure

## Acceptance Criteria

- search works
- valid articles can be selected
- real article content loads
- navigation is server-validated
- close detection works with real article links
- all tests pass
- PROJECT_STATE.md updated

STOP.

---

# Phase 4 — First-Person Frontend

## Goal

Build the unique first-person WikiRace visual experience and complete a single-browser game flow.

Do not add WebSocket multiplayer yet.

## Environment

Build the page as a first-person desk scene.

Use HTML/CSS only.

Do not use Three.js or WebGL.

Create components conceptually like:

GameEnvironment
RoomBackground
DeskSurface
DeskLamp
CoffeeCup
Notebook
Laptop
LaptopDisplay

LaptopDisplay contains the actual application.

## Visual Style

Theme:

cozy dark university study/dorm room.

Use:

- warm desk lamp glow
- dark neutral background
- realistic generic laptop
- keyboard/base
- coffee cup
- notebook
- subtle desk texture
- restrained shadows
- screen glow

Do not use brand logos.

Laptop should occupy approximately 80–85% of usable desktop width.

## Parallax

Add subtle mouse parallax.

Approximately 2–4px.

Disable when prefers-reduced-motion is enabled.

## Landing Screen

Inside laptop:

- WikiRace title
- display name input
- Create Room
- Join Room
- room code input

## Create Room

Host configures:

- start article autocomplete
- target article autocomplete
- timer
- unlimited option

## Lobby

Show:

- room code
- Copy Invite Link
- players
- host badge
- ready status
- start article
- target article
- timer
- Ready button
- host Start button

## Countdown

Show:

3
2
1
GO

Use server startsAt.

## Race Layout

Suggested structure:

top HUD:

- target
- timer
- click count
- Back button

main:

- article title/content

side:

- player list
- opponent current article
- click count
- connection status
- close warning

## Article Links

Intercept safe WikiRace internal article links.

On click:

- generate UUID actionId
- send navigation intent
- prevent accidental duplicate submission
- update only after server confirmation

## Back

Visible in-game Back button.

Generate actionId.

Call server BACK endpoint.

## Timer

Display smoothly client-side.

Derive from server timestamps.

Do not make frontend timer authoritative.

## Sudden Death

Show strong red visual state.

Example:

SUDDEN DEATH
+00:08

## Close Warning

Normal opponent:

Alex
Mathematics
6 clicks

Close opponent:

Alex
🔴 CLOSE!
7 clicks

Do not display hidden article.

Show warning:

⚠ Alex is one click away!

## Sounds

Add lightweight:

- countdown
- GO
- close warning
- win
- lose

Add mute control.

No continuous background music.

## Responsive Design

Desktop:

full environment.

Tablet:

simplified environment.

Mobile:

game UI dominates, decorations mostly hidden.

## Accessibility

Support:

- keyboard focus
- semantic controls
- reduced motion
- readable contrast
- text/icon in addition to red warning

## Temporary Multiplayer Limitation

Without WebSockets, use existing REST state where needed.

Do not create a large polling architecture that will immediately be replaced.

## Tests

Test useful frontend logic such as:

- timer formatting
- display name validation
- room code handling
- close rendering
- movement intent creation

## Acceptance Criteria

Single browser can:

- load first-person environment
- create room
- configure race
- reach lobby
- start
- see countdown
- navigate Wikipedia
- use Back
- see click count
- see timer
- enter Sudden Death if applicable
- reach target
- see result state

Frontend typecheck passes.

Frontend build passes.

PROJECT_STATE.md updated.

STOP.

---

# Phase 5 — Real-Time Multiplayer

## Goal

Support reliable 2–4 player real-time races.

## Technology

Add:

- Spring WebSocket
- STOMP

REST remains the command path.

WebSocket is used for server-to-client state updates.

## Connection

Client connects on room entry.

Use player session token.

Subscribe only to authorized room state.

## Events

Support event types conceptually equivalent to:

- ROOM_UPDATED
- PLAYER_JOINED
- PLAYER_READY_CHANGED
- SETTINGS_UPDATED
- COUNTDOWN_STARTED
- RACE_STARTED
- PLAYER_MOVED
- PLAYER_CLOSE_CHANGED
- PLAYER_DISCONNECTED
- PLAYER_RECONNECTED
- SUDDEN_DEATH_STARTED
- RACE_FINISHED

## RoomEvent

Include:

- event type
- room code
- room version
- occurredAt
- public room snapshot

## Versioning

Frontend tracks latest room version.

Ignore stale events.

## Real-Time Behavior

Synchronize:

- join
- ready states
- host setting changes
- settings reset ready states
- countdown
- race start
- movement
- click counts
- current articles
- close state
- sudden death
- winner
- finish results

## Close Redaction

When player becomes close:

WebSocket payload must not contain their current article.

Do not rely on CSS hiding.

## Winner

Winner transition must occur atomically.

All clients receive FINISHED state immediately.

## Disconnect

Detect disconnect.

During active race:

- keep player in room
- allow reconnect
- show disconnected status if useful
- race continues

## Reconnect

Refresh browser.

Client loads:

- room code
- player token

from localStorage.

Fetch current server state.

Reconnect WebSocket.

Continue race.

## Manual Test

Use four browser contexts/windows.

Verify:

- A creates
- B joins
- C joins
- D joins
- fifth player rejected
- ready sync
- host start
- synchronized countdown
- movement sync
- close warning
- article redaction
- exactly one winner
- results transition everywhere

## Tests

Test:

- event versioning
- redacted payload
- simultaneous moves
- simultaneous winners
- exactly one winner
- duplicate action
- room isolation

## Acceptance Criteria

Four browsers can complete a full race in real time.

No manual refresh needed for normal synchronization.

PROJECT_STATE.md updated.

STOP.

---

# Phase 6 — Reconnect and Network Hardening

## Goal

Make temporary sessions and navigation robust.

Do not add new product features.

## Reconnect

Verify refresh works during:

- WAITING
- COUNTDOWN
- ACTIVE
- SUDDEN_DEATH
- FINISHED

Restore:

- player identity
- room state
- current article
- click count
- timer
- results

## localStorage

Store only:

- roomCode
- playerSessionToken

Do not treat localStorage as authoritative.

## Browser Back

Improve native browser Back behavior during an active race.

Goal:

browser Back should preferably trigger game BACK rather than unexpectedly leaving the game.

Use History API carefully.

The in-game Back button remains canonical.

Do not create routing loops.

Do not allow browser history to become authoritative gameplay state.

## Idempotency Hardening

Test:

- double click
- network retry
- rapid click
- repeated actionId

## Rate Limiting

Add basic in-memory per-player movement rate limiting.

Allow legitimate fast use.

Reject unreasonable spam.

Return structured 429 RATE_LIMITED error.

## Lobby Disconnect Cleanup

Disconnected WAITING player receives approximately 5-minute grace period.

After grace period:

remove stale lobby player.

Do not remove active-race players before race finishes.

## Room Cleanup

Clean stale:

- abandoned WAITING rooms
- old FINISHED rooms

Avoid unbounded in-memory room growth.

## Acceptance Criteria

- refresh reconnect works
- duplicate movement is safe
- basic rate limiting works
- stale rooms are cleaned
- browser history behavior is reasonable
- PROJECT_STATE.md updated

STOP.

---

# Phase 7 — PostgreSQL Persistence

## Goal

Persist completed race results only.

Do not move active room state into PostgreSQL.

## Dependencies

Add:

- Spring Data JPA
- PostgreSQL driver
- Flyway

## Persist

Store completed race information including:

- race ID
- room code
- start article
- target article
- configured timer
- unlimited flag
- startedAt
- suddenDeathStartedAt
- finishedAt
- winner

Per-player result:

- display name
- click count
- final article
- winner flag
- ordered visit log

Do not store:

- session tokens
- WebSocket data
- runtime locks

## Schema

Use a clear maintainable design.

Either:

- normalized path-step rows

or:

- PostgreSQL JSONB

is acceptable if justified.

## Runtime Failure

If database persistence fails after a winner is already determined:

- race still finishes
- winner remains correct
- log persistence failure
- do not corrupt runtime state

## Result Endpoint

Implement:

GET /api/races/{id}/results

Use DTO.

## Flyway

Use versioned migrations.

Do not use Hibernate schema auto-create in production.

## Tests

Use integration tests.

Prefer Testcontainers when appropriate.

Do not require the developer to manually run PostgreSQL for automated tests.

## Acceptance Criteria

- completed results persist
- visit-log ordering is preserved
- completed result survives backend restart
- active rooms remain in memory
- PROJECT_STATE.md updated

STOP.

---

# Phase 8 — Visual and UX Polish

## Goal

Make the project portfolio-ready without adding major backend systems.

## Environment Polish

Improve:

- room lighting
- desk composition
- laptop depth
- keyboard
- lamp
- coffee cup
- notebook
- screen glow
- background depth

Keep atmosphere restrained.

## Transitions

Polish:

- landing -> lobby
- lobby -> countdown
- countdown -> race
- article change
- close alert
- sudden death
- win/loss
- results

Respect reduced motion.

## Close Alert

Use:

- red player row
- visible warning
- restrained screen-edge pulse

Do not use aggressive flashing.

## Winner

Add a satisfying lightweight finish effect.

CSS-only animation is preferred.

No heavy particle engine.

## Loss

Clearly show who won.

## Loading States

Polish:

- article loading
- server waiting
- Wikipedia unavailable
- reconnecting

## Error UX

Polish:

- room full
- invalid room
- race already started
- host unavailable
- invalid navigation
- rate limit
- Wikipedia error

## Performance

Check:

- unnecessary renders
- event listener cleanup
- WebSocket cleanup
- parallax listener cost
- large article HTML

## Acceptance Criteria

Application looks intentional and polished.

Visual changes do not reduce gameplay usability.

PROJECT_STATE.md updated.

STOP.

---

# Phase 9 — Testing and Engineering Hardening

## Goal

Audit correctness, security, and code quality.

Do not add product features.

## Game Tests

Ensure coverage for:

- room creation
- join
- max players
- ready system
- host permissions
- settings
- countdown
- active state
- unlimited
- sudden death
- navigate
- Back
- history cursor
- forward truncation
- click count
- visit log
- close detection
- redaction
- winner
- one winner only
- idempotency
- rate limit
- disconnect
- reconnect
- cleanup

## Wikipedia Tests

Test:

- search
- canonicalization
- redirects
- namespace filtering
- outgoing links
- sanitized HTML
- malicious input
- timeout
- upstream failure

## API Tests

Check:

- validation
- HTTP status codes
- structured errors
- player token authorization

## WebSocket Tests

Check:

- room subscription
- room isolation
- state version ordering
- cleanup

## Persistence Tests

Check:

- completed race save
- player result save
- visit-log ordering

## Frontend Tests

Test only important behavior.

Do not target artificial 100% coverage.

## Security Audit

Verify:

- client cannot set click count
- client cannot set winner
- client cannot set current article
- client cannot bypass link validation
- hidden close article is absent from payload
- unsafe HTML removed
- external links cannot become moves
- session tokens are never broadcast
- tokens are not logged
- display names render safely

## Dependency Review

Remove unused dependencies.

## Code Cleanup

Remove:

- dead code
- debug logs
- unused DTOs
- stale temporary code
- unused components

## Acceptance Criteria

All builds pass.

All tests pass.

No known critical gameplay correctness bug remains.

PROJECT_STATE.md updated.

STOP.

---

# Phase 10 — Docker and CI

## Goal

Make the repository reproducible and professionally buildable.

## Docker

Create production-oriented Dockerfiles for:

- backend
- frontend

Create root:

compose.yaml

Services:

- backend
- frontend
- PostgreSQL

Goal:

docker compose up --build

starts the stack.

## Configuration

Use environment variables.

Document required variables.

Never commit secrets.

## Health Checks

Add health checks where useful.

## GitHub Actions

Create CI for push / pull request.

Backend:

- mvn test
- mvn package

Frontend:

- npm ci
- typecheck
- tests
- build

## Acceptance Criteria

Fresh clone can build using documented commands.

Docker stack starts.

CI config is valid.

PROJECT_STATE.md updated.

STOP.

---

# Phase 11 — Deployment

## Goal

Prepare and perform simple portfolio deployment.

Preferred architecture:

Frontend:

- Vercel

Backend:

- Railway

Database:

- Railway PostgreSQL

If provider constraints create unnecessary complexity, use the simplest equivalent deployment.

## Production Configuration

Configure:

- production API URL
- WebSocket URL
- CORS
- database URL
- Flyway
- environment variables
- health endpoint

## Verify Production

Production must support:

- load frontend
- create room
- join room
- WebSocket connection
- race
- close warning
- winner
- database result persistence

## Do Not Add

Do not introduce:

- Redis
- Kubernetes
- microservices
- distributed locks

## Documentation

Document exact deployment steps.

## Acceptance Criteria

Production application is reachable and playable.

PROJECT_STATE.md updated.

STOP.

---

# Phase 12 — Portfolio Documentation

## Goal

Finalize repository documentation for SWE internship use.

Do not change product behavior.

## README

Create professional README.

Avoid exaggerated marketing language.

Do not claim features that do not exist.

Recommended structure:

# WikiRace

One-sentence project description.

## Demo

Screenshot / GIF / video placeholder.

## What It Does

Explain multiplayer Wikipedia racing.

## Features

Only implemented features.

## Architecture

Include Mermaid diagram.

Conceptually:

React clients
   |
REST commands
   |
Spring Boot
   |-- Game Engine
   |-- Wikipedia API
   |-- WebSocket
   |-- PostgreSQL

Spring Boot
   |
STOMP room updates
   |
React clients

## Server-Authoritative Navigation

Explain:

server knows current article A

client requests destination B

server verifies:

B ∈ outgoingLinks(A)

then commits movement.

Explain why this prevents trivial cheating.

## Close-to-Target Mechanic

Explain:

target ∈ outgoingLinks(currentArticle)

Then:

- opponent becomes CLOSE
- current article is redacted

## Concurrency

Explain:

- per-room synchronization
- one winner
- idempotent actions

## Real-Time Architecture

Explain REST commands + WebSocket snapshots/events.

## Tech Stack

## Running Locally

## Docker

## Tests

## Project Structure

## Engineering Decisions

Explain:

- active rooms in memory
- completed races in PostgreSQL
- CSS first-person scene instead of 3D engine
- official Wikimedia APIs
- server-side sanitization
- WebSocket for broadcasts
- REST for commands

## Known Limitations

Examples:

- active races do not survive backend restart
- no accounts
- no global matchmaking
- MVP assumes one backend instance

## Future Improvements

Possible:

- Redis/shared runtime state
- horizontal scaling
- matchmaking
- leaderboard
- spectator mode
- bounded shortest-path analysis

## Architecture Docs

Ensure:

docs/architecture.md

matches the real final implementation.

## API Docs

Ensure:

docs/api.md

matches the final endpoints.

## Resume Notes

Create:

docs/resume-notes.md

Include 2–3 factual resume bullet suggestions.

Do not invent performance metrics.

Example style:

- Built a real-time multiplayer Wikipedia racing game using Java/Spring Boot and React, synchronizing up to four players through WebSockets while keeping race state server-authoritative.
- Implemented validated Wikipedia navigation, idempotent movement commands, reconnect handling, and concurrency-safe winner selection.
- Integrated Wikimedia APIs with HTML sanitization, caching, link validation, and PostgreSQL persistence for completed race results.

Only retain bullets supported by the actual finished project.

## Acceptance Criteria

- README matches implementation
- architecture docs match implementation
- API docs match implementation
- resume bullets are truthful
- PROJECT_STATE.md updated

STOP.