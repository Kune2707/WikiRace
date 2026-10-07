You are my senior software engineer and implementation partner for a portfolio project called WikiRace.

This project is intended to become a polished but intentionally scoped SWE internship resume project.

The goal is NOT to build the largest possible application.

The goal is to build a small, reliable, technically interesting application that demonstrates:

- Java backend engineering
- Spring Boot
- clean REST API design
- server-authoritative state
- real-time WebSocket communication
- concurrency handling
- third-party API integration
- caching
- reconnect logic
- input validation
- persistence
- testing
- Docker
- CI
- polished frontend engineering
- thoughtful UX

The initial MVP deadline is approximately 7 days.

==================================================
CRITICAL WORKING RULES
==================================================

1. DO NOT build the entire application at once.

2. Only implement the phase I explicitly ask you to implement.

3. Never implement future-phase features "because they are easy."

4. Before coding a phase:
   - inspect the repository,
   - inspect PROJECT_SPEC.md,
   - inspect PROJECT_STATE.md,
   - summarize what already exists,
   - describe the current phase plan,
   - list files expected to be created or modified.

5. Do not rewrite unrelated working code.

6. Do not introduce abstractions unless they solve a real current problem.

7. Prefer understandable code over clever code.

8. Preserve architecture decisions already documented unless there is a real technical blocker.

9. If architecture must change:
   - explain why,
   - describe impact,
   - update PROJECT_STATE.md.

10. Never silently change game rules.

11. Never silently weaken server-side validation.

12. The server must remain authoritative for gameplay.

13. After each phase:
   - run relevant tests,
   - run backend build,
   - run frontend typecheck/build if frontend changed,
   - fix errors caused by the phase,
   - update PROJECT_STATE.md,
   - summarize work,
   - STOP.

14. Do not begin the next phase automatically.

15. Do not create placeholder implementations for future systems.

16. Avoid TODO-heavy code.

17. Do not generate code that is unused by the current phase.

18. Configuration and secrets must not be committed.

19. External API calls must not be used in automated tests.

20. Optimize for an architecture I can explain confidently during a SWE interview.

==================================================
PROJECT NAME
==================================================

WikiRace

==================================================
PROJECT CONCEPT
==================================================

WikiRace is a real-time multiplayer Wikipedia racing game.

A room contains 2–4 players.

Every player receives:

- the same starting Wikipedia article
- the same target Wikipedia article

The target is visible to everyone from the beginning.

Players navigate from article to article by clicking legitimate links contained in Wikipedia articles.

The first player to reach the target article wins.

The server must verify every move.

A client can NEVER simply claim:

"I am now on the target article."

==================================================
CORE GAMEPLAY
==================================================

Maximum players:
4

Minimum players to start:
2

Players use guest display names.

No accounts or login system exist in the MVP.

Display name rules:

- 2–20 characters
- no emoji
- no profanity filter
- allow reasonable letters, numbers, spaces, hyphens, apostrophes, periods, and underscores
- reject unsupported symbols or emoji

Use Unicode letters/numbers if implementation is straightforward.

Example acceptable validation idea:

[\p{L}\p{N} ._'-]{2,20}

==================================================
ROOMS
==================================================

Every room has a 6-character human-readable code.

Use an alphabet that avoids confusing characters where practical.

For example:

ABCDEFGHJKLMNPQRSTUVWXYZ23456789

Example room code:

7FK2QM

Rooms must also have shareable URLs:

/room/7FK2QM

A player can:

- create a room
- join a room using its code
- join using a shareable link

==================================================
HOST
==================================================

The room creator becomes host.

The host controls pre-race settings.

The host chooses:

- starting article
- target article
- time limit

The host can update settings while the room is still WAITING.

Changing any gameplay setting resets ALL player ready states to false.

The host cannot start until:

- there are at least 2 players
- there are at most 4 players
- all players are Ready
- start article is valid
- target article is valid
- start != target

==================================================
READY SYSTEM
==================================================

Every player, including the host, must explicitly become Ready.

When everybody is ready:

the host may press Start Race.

If host changes:

- start article
- target article
- timer

then all players become Not Ready again.

==================================================
ARTICLE SELECTION
==================================================

The host manually chooses:

- start article
- target article

Use English Wikipedia only for the MVP.

Provide Wikipedia autocomplete search for choosing articles.

The server must verify that selected articles actually exist.

Do not allow:

startArticle == targetArticle

==================================================
TIME LIMIT
==================================================

The host chooses a time limit.

The UI must support:

- custom timed races starting at 3 minutes minimum
- Unlimited

A practical UI is:

[ number input ] minutes
minimum 3

and:

[ Unlimited ]

The server stores the duration authoritatively.

Unlimited race:

- timer counts upward
- there is no expiration

Timed race:

- timer counts down

==================================================
SUDDEN DEATH
==================================================

If a timed race reaches zero and nobody has reached the target:

the race does NOT end.

Instead:

ACTIVE -> SUDDEN_DEATH

All remaining players continue racing.

The first person who reaches the target during Sudden Death wins.

There is no further timeout.

UI should clearly change to something similar to:

SUDDEN DEATH
+00:12

The overtime timer counts upward from the moment normal time expired.

==================================================
WIN CONDITION
==================================================

The first player whose server-validated current article becomes the target article wins.

Once a winner is committed:

- winner cannot change
- race becomes FINISHED
- navigation stops for everybody
- timer stops
- server broadcasts result

There is only one winner.

==================================================
COUNTDOWN
==================================================

Before race start:

WAITING
   ↓
COUNTDOWN
   ↓
ACTIVE

Use a synchronized 3-second countdown:

3
2
1
GO

The server owns the authoritative startsAt timestamp.

Clients display the countdown using the server timestamp.

Do not let each browser independently decide when the race starts.

==================================================
BACK NAVIGATION
==================================================

Back navigation is allowed.

Back counts as ONE click.

Example:

A -> B -> C

click count = 2

Back:

C -> B

click count = 3

The server must track navigation history.

Do not trust the browser/client to specify the previous article.

The BACK action should not contain a destination.

Server decides what the previous article is.

Maintain browser-like navigation history using:

- history entries
- history cursor

Example:

A -> B -> C

history:
[A, B, C]

cursor = C

Back:

cursor moves to B

If player navigates from B to D after going back:

truncate forward history.

New history:

[A, B, D]

This should behave like normal browser navigation.

Also maintain a separate visit log representing the actual sequence of race actions.

Example:

A
B
C
B
D

That visit log is useful in results.

A successful BACK action increments click count.

An invalid back action when no previous page exists should be rejected/no-op without incrementing clicks.

The frontend should provide a visible in-game Back control.

Where practical, browser Back during an active race should trigger the same server-authoritative BACK action rather than accidentally leaving the game.

Do not compromise routing stability to achieve this. The in-game Back control is the canonical implementation.

==================================================
PLAYER PATH
==================================================

Maintain two distinct concepts:

1. Navigation history

Used for browser-style Back behavior.

2. Visit log

Used to show the actual race journey.

Example visit log:

Computer Science
Algorithm
Graph Theory
Algorithm
Mathematics
Target

Every successful NAVIGATE or BACK action adds the resulting article to the visit log.

The starting article is the first entry.

==================================================
CLICK COUNT
==================================================

Click count begins at 0.

Every successful gameplay movement increments click count exactly once.

Successful actions that increment click count:

- NAVIGATE
- BACK

The following must NOT increment it:

- rejected navigation
- invalid back
- duplicate action request
- race already finished
- request before race starts
- network retry using same actionId

==================================================
SERVER AUTHORITATIVE DESIGN
==================================================

This is one of the most important engineering requirements.

Clients may be malicious.

Assume users can:

- inspect React source
- modify JavaScript
- manually call APIs
- replay network requests
- modify localStorage
- fake UI state

Therefore the backend owns:

- room state
- players
- host
- ready state
- start article
- target article
- timer settings
- countdown
- start timestamp
- current article
- click count
- navigation history
- history cursor
- visit log
- whether player is close
- winner
- finish timestamp
- race state

The frontend is only a presentation and interaction layer.

==================================================
VALID ARTICLE NAVIGATION
==================================================

Suppose server knows player is currently at article A.

Client clicks article B.

Client sends only an intent such as:

{
  "actionId": "...",
  "destinationArticle": "B"
}

The client does NOT get to submit:

- current article
- click count
- path
- isClose
- winner
- elapsed time

Server performs:

1. authenticate player session token
2. find server-known current article A
3. verify race allows navigation
4. normalize B
5. obtain legitimate outgoing Wikipedia links from A
6. verify:

B ∈ outgoingLinks(A)

7. if invalid:
   reject request

8. if valid:
   update server state
   increment click count once
   update navigation history
   append B to visit log
   set B as current article
   determine close-to-target state
   determine whether B == target
   broadcast new state

==================================================
IDEMPOTENCY
==================================================

Every movement action has a UUID actionId.

Example:

{
  "actionId": "b631...",
  "destinationArticle": "Graph theory"
}

The server keeps a bounded set/cache of recently processed action IDs for each player.

If the same actionId is received again:

- do NOT execute it twice
- do NOT increment click count twice
- return an idempotent safe response

This protects against:

- accidental double click
- browser retry
- connection retry
- request replay

BACK actions also have actionId.

==================================================
CLOSE TO TARGET MECHANIC
==================================================

This is a core gameplay feature.

A player is considered "close to the target" when their current article directly contains a legitimate link to the target.

Mathematically:

target ∈ outgoingLinks(currentArticle)

This means the player is exactly ONE legitimate click away.

When this becomes true:

- mark that player as close
- broadcast the change
- opponents see a red alert
- player's current article becomes HIDDEN from opponent status displays

Example normal opponent state:

Alex
Mathematics
6 clicks

When Alex becomes one hop away:

Alex
🔴 CLOSE!
7 clicks

DO NOT display Alex's current article while Alex is close.

The alert should visually create urgency.

Recommended UI:

🔴 ALEX IS CLOSE!

or:

Alex            🔴 CLOSE!

Use both:

- red player styling
- visible warning

The close player's own article obviously remains visible to themselves as their main article.

Do not explicitly reveal to that player a special "you are close" notification beyond what they can infer from seeing the target link themselves.

If the player moves away from a one-hop-away article:

isClose becomes false

Their current article becomes visible to opponents again.

==================================================
OPPONENT INFORMATION
==================================================

Normally opponents may see:

- display name
- current article
- click count
- connected/disconnected state

Example:

Alex
Mathematics
6 clicks

BUT if Alex is close:

current article must be redacted:

Alex
🔴 CLOSE!
7 clicks

Do not leak the hidden article through WebSocket payloads.

==================================================
WIKIPEDIA RULES
==================================================

Use English Wikipedia only.

Use official Wikimedia / MediaWiki APIs.

Avoid scraping arbitrary rendered browser pages if official APIs provide the required information.

Support normal main-namespace Wikipedia articles.

Reject navigation targets in namespaces such as:

- File:
- Category:
- Help:
- Talk:
- Special:
- User:
- Template:
- Portal:
- external URLs

Disambiguation pages ARE allowed.

Redirects should be resolved consistently.

Canonical article titles should be used throughout server state.

==================================================
ARTICLE SEARCH
==================================================

Provide server-backed autocomplete for:

- starting article
- target article

Example:

"Albert Ein"

may return:

- Albert Einstein
- Albert Einstein College of Medicine
- ...

Search result DTO should be small:

- title
- optional description/snippet if easily available

==================================================
ARTICLE CONTENT
==================================================

The race interface should render a WikiRace-styled version of Wikipedia content.

Do not iframe Wikipedia.

Do not make the laptop screen a raw embedded browser.

The visual style should be custom WikiRace styling while preserving readable article structure:

- title
- headings
- paragraphs
- lists
- legitimate article links

For the MVP, images are OPTIONAL and should not block completion.

Prefer text-first content.

The backend should sanitize and normalize article content.

A reasonable implementation is:

MediaWiki API
    ↓
HTML/article data
    ↓
server sanitization with Jsoup
    ↓
safe HTML DTO
    ↓
frontend defense-in-depth sanitization if HTML is rendered

If sanitized HTML is used:

- strip scripts
- strip styles
- strip forms
- strip iframes
- strip event handlers
- strip unsafe attributes
- remove or neutralize external links
- mark legitimate internal article links with article-title metadata

Example generated link:

<a
  href="#"
  data-wiki-title="Graph theory"
>
  graph theory
</a>

The frontend intercepts legitimate article clicks and sends a navigation intent to the backend.

Never trust a link merely because it existed in rendered client HTML.

Backend still validates it independently.

==================================================
WIKIPEDIA CACHE
==================================================

Avoid hitting Wikipedia for every repeated operation.

Cache useful article data such as:

- canonical title
- sanitized article content
- outgoing article links

Use a bounded cache with expiration.

A small library such as Caffeine is acceptable.

Do not implement an unbounded ConcurrentHashMap cache.

Example reasonable policy:

- several hundred entries
- expiration after a reasonable period
- configurable values

Do not prematurely optimize beyond this.

==================================================
EXTERNAL API FAILURE
==================================================

Handle:

- article not found
- redirect
- timeout
- rate limiting
- temporary Wikimedia error
- malformed response

Do not crash a race thread.

Return clear errors.

Use appropriate request timeout.

Use an identifiable User-Agent appropriate for Wikimedia API usage.

==================================================
REAL-TIME COMMUNICATION
==================================================

Use:

Spring WebSocket + STOMP

Use REST for state-changing commands.

Use WebSocket for server -> client state/event broadcasting.

This keeps mutation logic easier to test and reason about.

Examples:

REST:
POST navigate
POST back
POST ready
POST start

WebSocket:
ROOM_UPDATED
COUNTDOWN_STARTED
RACE_STARTED
PLAYER_MOVED
PLAYER_CLOSE_CHANGED
SUDDEN_DEATH_STARTED
RACE_FINISHED
PLAYER_DISCONNECTED
PLAYER_RECONNECTED

==================================================
ROOM EVENT MODEL
==================================================

Prefer broadcasting server-generated snapshots rather than requiring the client to reconstruct complex state using tiny patches.

Suggested structure:

RoomEvent {
    type
    roomCode
    version
    occurredAt
    room
}

Room version increases whenever authoritative room state changes.

Clients ignore events older than their latest known version.

This protects against out-of-order delivery.

==================================================
PUBLIC ROOM SNAPSHOT
==================================================

Never serialize internal runtime objects directly.

Use DTOs.

Public room/player DTOs should expose only permitted information.

When a player is close:

their currentArticle must be null/redacted in the shared room snapshot.

Example:

{
  "displayName": "Alex",
  "clickCount": 7,
  "closeToTarget": true,
  "currentArticle": null
}

When not close:

{
  "displayName": "Alex",
  "clickCount": 6,
  "closeToTarget": false,
  "currentArticle": "Mathematics"
}

==================================================
CONCURRENCY
==================================================

Room state must be concurrency-safe.

Multiple players may move at the same time.

Two players may reach the target nearly simultaneously.

Requirements:

- exactly one winner
- no corrupted histories
- no double click counting
- race status transitions remain valid

Use a simple per-room synchronization mechanism such as:

ReentrantLock

or another clearly justified approach.

Do not introduce distributed locking.

Important rule:

avoid holding a room lock across slow network calls to Wikipedia where practical.

Use cached article data when possible.

If external data must be fetched outside the lock:

re-check state before committing the mutation.

==================================================
RACE STATES
==================================================

Use:

WAITING
COUNTDOWN
ACTIVE
SUDDEN_DEATH
FINISHED

Allowed transitions:

WAITING -> COUNTDOWN
COUNTDOWN -> ACTIVE
ACTIVE -> SUDDEN_DEATH
ACTIVE -> FINISHED
SUDDEN_DEATH -> FINISHED

Do not allow arbitrary backwards transitions.

==================================================
RECONNECT
==================================================

There are no user accounts.

When a player creates or joins a room:

server issues a secure opaque player session token.

Client stores it in localStorage.

The token represents the player's temporary race identity.

Do not rely only on playerId.

After refresh:

client can reconnect using:

- room code
- player session token

Server restores:

- player identity
- current race state
- current article
- click count
- relevant room state

==================================================
DISCONNECT
==================================================

If a player disconnects during an ACTIVE or SUDDEN_DEATH race:

- do NOT cancel race
- player remains in room
- player can reconnect
- opponents see disconnected status if useful

Race continues.

During WAITING:

a disconnected player may retain their slot for approximately 5 minutes.

If they do not return:

their lobby slot may be removed.

During an active race:

retain their player entry until race finishes.

If host disconnects while waiting:

allow a reconnect grace period.

Do not build complicated host migration unless it becomes necessary.

==================================================
PLAYER TOKEN
==================================================

Use player session tokens for REST commands.

Example header:

X-Player-Token: <opaque-token>

Do not expose tokens in room broadcasts.

For WebSocket subscriptions, validate the player token during STOMP connection/subscription if practical.

Do not build a complete authentication framework.

==================================================
BASIC RATE LIMITING
==================================================

Implement simple per-player rate limiting for gameplay actions.

Goal:

prevent absurd request spam.

Something roughly equivalent to:

5–10 movement actions per second

is sufficient.

Do not build enterprise distributed rate limiting.

==================================================
FRONTEND STACK
==================================================

Use:

- React
- TypeScript
- Vite
- React Router
- CSS Modules or clean component-scoped CSS
- Vitest
- React Testing Library where useful

Avoid unnecessary frontend state libraries for the MVP.

Use normal React state/context/hooks where sufficient.

==================================================
BACKEND STACK
==================================================

Use:

- Java 21
- Spring Boot
- Maven
- Spring Web
- Spring Validation
- Spring WebSocket
- Spring Data JPA in persistence phase
- PostgreSQL in persistence phase
- Flyway in persistence phase
- Jsoup for sanitization
- Caffeine if used for article caching
- JUnit
- Spring Boot Test

==================================================
ACTIVE VS PERSISTED STATE
==================================================

Active races remain in memory.

Completed race history is persisted to PostgreSQL.

This is intentional.

Do not attempt to persist every live WebSocket state mutation.

Document this architecture decision.

A server restart may destroy active races in the MVP.

That is acceptable and should be documented as a future scaling improvement.

==================================================
COMPLETED RACE DATA
==================================================

Persist completed races containing useful information such as:

Race:

- id
- roomCode
- startArticle
- targetArticle
- configuredTimeLimit
- unlimited flag
- startedAt
- suddenDeathStartedAt if applicable
- finishedAt
- winner

For each player:

- displayName
- clickCount
- final article
- whether winner
- ordered visit log

Do not persist player session tokens.

==================================================
NO LEADERBOARD
==================================================

Do not build a global leaderboard in the MVP.

==================================================
NO USER ACCOUNTS
==================================================

Do not build:

- signup
- login
- OAuth
- passwords
- profiles

==================================================
NO CHAT
==================================================

Do not build chat.

==================================================
NO MATCHMAKING
==================================================

Do not build global matchmaking.

==================================================
NO SHORTEST PATH CRAWLER
==================================================

Do not build Wikipedia BFS shortest-path analysis during the core 7-day MVP.

It may be added later as a bonus phase.

==================================================
FIRST-PERSON VISUAL DESIGN
==================================================

This is a defining part of the project.

The application must NOT look like a standard dashboard.

The player should feel like they are sitting at a desk in a cozy, dark university study/dorm room.

The camera is first person.

Directly in front of the player is a laptop.

The actual WikiRace application runs INSIDE the laptop screen.

Concept:

                  dark room

             subtle wall lighting

                  lamp glow

           ╭────────────────────╮
           │                    │
           │      LAPTOP        │
           │                    │
           │     WikiRace       │
           │                    │
           ╰────────────────────╯
               keyboard

     coffee                    notebook

══════════════════ DESK ══════════════════

The laptop is centered and should occupy roughly 80–85% of usable desktop width.

The environment exists to create atmosphere.

The laptop screen remains the primary usability surface.

==================================================
ROOM STYLE
==================================================

Visual direction:

cozy dark university study room

Use:

- warm desk lamp glow
- dark room background
- subtle desk texture
- realistic generic unbranded laptop
- keyboard
- coffee cup
- notebook
- optional subtle background/window shapes
- slight laptop screen glow
- restrained shadows

Do NOT copy Apple logos or branding.

==================================================
NO 3D ENGINE
==================================================

Do not use:

- Three.js
- WebGL game engine
- Unity
- heavy 3D libraries

Use:

- HTML
- CSS perspective
- transforms
- gradients
- shadows
- SVG/CSS shapes when needed

==================================================
PARALLAX
==================================================

Add extremely subtle mouse parallax if inexpensive.

Approximately 2–4 pixels of scene movement is enough.

It must:

- never interfere with reading
- be disabled with prefers-reduced-motion
- be easy to turn off if performance suffers

==================================================
SOUND
==================================================

Include lightweight sound effects for:

- countdown
- GO
- close-to-target warning
- win
- lose

Provide a mute button.

Default sound level should be restrained.

Do not add continuous background music.

==================================================
LAPTOP SCREEN DESIGN
==================================================

The laptop screen uses a custom WikiRace interpretation of Wikipedia.

It should NOT exactly clone wikipedia.org.

Preserve:

- article readability
- article hierarchy
- familiar serif/body reading experience if appropriate
- visible links

Add WikiRace game UI around it.

Suggested race layout:

┌────────────────────────────────────────────────────┐
│ TARGET: Quantum mechanics       04:23     6 clicks │
├─────────────────────────────────────┬──────────────┤
│                                     │ PLAYERS      │
│ Computer Science                    │              │
│                                     │ You          │
│ Computer science is the study...    │ 6 clicks     │
│                                     │              │
│ Algorithms                          │ Alex         │
│ Mathematics                         │ Mathematics  │
│ Information                         │ 7 clicks     │
│                                     │              │
│                                     │ Sarah        │
│                                     │ 🔴 CLOSE!    │
│                                     │ 8 clicks     │
└─────────────────────────────────────┴──────────────┘

The article section scrolls inside the laptop screen.

The entire real browser page should not need to scroll excessively during normal desktop play.

==================================================
UI SCREENS
==================================================

Implement:

1. LandingScreen

Contains:

- WikiRace title/logo treatment
- display name input
- Create Room
- Join Room
- room code input

2. LobbyScreen

Contains:

- room code
- copy share link
- player list
- host indicator
- ready status
- host article settings
- Wikipedia autocomplete
- timer settings
- Ready button
- Start button for host

3. CountdownOverlay

3
2
1
GO

4. RaceScreen

Contains:

- target
- timer
- click count
- in-game Back button
- current article
- article content
- clickable internal links
- opponents
- close-to-target warnings
- connection status

5. SuddenDeathState

Very obvious visual transition.

6. ResultsScreen

Contains:

- winner
- final time
- click count
- each player's visit log
- race settings summary

Do not build rematch in the initial core MVP unless everything else is already stable.

7. Error/Disconnected states

==================================================
RESULTS SCREEN
==================================================

When race finishes show:

🏆 WINNER

Player name

Time
Click count

Then player paths.

Example:

YOU

Computer Science
      ↓
Algorithm
      ↓
Graph theory
      ↓
Mathematics
      ↓
Quantum mechanics

Alex

Computer Science
      ↓
Technology
      ↓
...

Show all participating player visit logs.

==================================================
RESPONSIVE DESIGN
==================================================

Desktop is primary.

Desktop:

- full room atmosphere
- full laptop shell
- desk elements

Tablet:

- reduce decoration
- keep laptop recognizable

Mobile:

- prioritize game functionality
- laptop screen may essentially fill viewport
- decorative room elements may disappear

Do not sacrifice usability to preserve visual gimmicks on mobile.

==================================================
ACCESSIBILITY
==================================================

Use:

- semantic HTML
- keyboard-accessible controls
- visible focus states
- adequate contrast
- labels
- reduced-motion support
- mute option

Do not make crucial information dependent solely on animation.

Close-to-target warning should use:

- color
- text/icon

not color alone.

==================================================
ERROR MODEL
==================================================

Use structured API errors.

Example:

{
  "code": "INVALID_NAVIGATION",
  "message": "The selected article is not linked from your current article.",
  "timestamp": "..."
}

Potential codes:

ROOM_NOT_FOUND
ROOM_FULL
INVALID_PLAYER_TOKEN
INVALID_DISPLAY_NAME
NOT_HOST
PLAYER_NOT_READY
PLAYERS_NOT_READY
INVALID_RACE_STATE
INVALID_NAVIGATION
NO_BACK_HISTORY
DUPLICATE_ACTION
ARTICLE_NOT_FOUND
WIKIPEDIA_UNAVAILABLE
RATE_LIMITED
RACE_FINISHED
INVALID_ROOM_SETTINGS

Use appropriate HTTP status codes.

==================================================
LOGGING
==================================================

Log useful server events:

- room creation
- player join
- start
- navigation rejection
- sudden death
- winner
- Wikipedia failures
- reconnect
- disconnect

Do not log:

- player session tokens
- sensitive headers

==================================================
OUT OF SCOPE
==================================================

Do NOT implement during MVP unless explicitly requested later:

- user accounts
- OAuth
- matchmaking
- global leaderboard
- chat
- spectator mode
- friends
- native mobile app
- microservices
- Redis
- Kubernetes
- AI
- shortest-path crawling
- full Wikipedia mirroring
- complex anti-bot system
- full production authentication
- analytics platform
- payment
- advertising

==================================================
PROJECT FILES
==================================================

Use a monorepo approximately like:

wikirace/
├── backend/
├── frontend/
├── docs/
│   ├── architecture.md
│   └── api.md
├── PROJECT_SPEC.md
├── PROJECT_STATE.md
├── README.md
├── compose.yaml
└── .gitignore

PROJECT_SPEC.md:

Store the stable project specification.

PROJECT_STATE.md:

Keep concise current project state.

Use format:

# Current Phase

Phase X — ...

# Completed

- ...

# Architecture Decisions

- ...

# Current API

- ...

# Current Data Model

- ...

# Known Issues

- ...

# Next Phase

- ...

Do not turn PROJECT_STATE.md into a giant development diary.

It should exist so future Codex sessions can understand the repo without requiring the entire project specification to be repeated.

==================================================
PHASE DISCIPLINE
==================================================

When I say:

"Implement Phase X"

work ONLY on Phase X.

At the end print:

PHASE STATUS: PASS

or:

PHASE STATUS: FAIL

Then include:

Completed:
- ...

Tests:
- ...

Files changed:
- ...

Known limitations:
- ...

Manual verification:
- ...

PROJECT_STATE.md updated:
- yes/no

Then STOP.

Do not proceed to the next phase.

For now:

DO NOT IMPLEMENT THE APPLICATION.

First:

1. inspect the repository if one exists
2. save this project specification into PROJECT_SPEC.md
3. create PROJECT_STATE.md containing only:
   - project initialized
   - current phase = Phase 0
4. wait for Phase 0 instructions