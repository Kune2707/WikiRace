package com.wikirace.game.service;

import java.security.MessageDigest;
import java.time.Clock;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.wikirace.common.PlayerTokens;
import com.wikirace.dto.RoomSnapshotMapper;
import com.wikirace.dto.ArticleResponse;
import com.wikirace.exception.GameException;
import com.wikirace.game.model.*;
import com.wikirace.game.runtime.RoomRegistry;
import com.wikirace.wikipedia.ArticleData;
import com.wikirace.wikipedia.ArticleProvider;
import com.wikirace.wikipedia.WikiTitles;
import com.wikirace.websocket.RoomEvent;
import static com.wikirace.dto.GameDtos.*;
import static com.wikirace.exception.GameException.Code.*;

@Service
public class GameService {
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} ._'-]+");
    private final RoomRegistry rooms;
    private final ArticleProvider articles;
    private final Clock clock;
    private final int actionCapacity;
    private final Consumer<RoomEvent> publisher;
    private final com.wikirace.game.runtime.GameLimits limits;
    private final ApplicationEventPublisher applicationEvents;

    public GameService(RoomRegistry rooms, ArticleProvider articles, Clock clock,
                       int actionCapacity) {
        this(rooms, articles, clock, actionCapacity, event -> {});
    }

    public GameService(RoomRegistry rooms, ArticleProvider articles, Clock clock,
                       @Value("${app.game.action-record-capacity:2048}") int actionCapacity,
                       ApplicationEventPublisher publisher) {
        this(rooms, articles, clock, actionCapacity, publisher, com.wikirace.game.runtime.GameLimits.defaults());
    }

    @Autowired
    public GameService(RoomRegistry rooms, ArticleProvider articles, Clock clock,
                       @Value("${app.game.action-record-capacity:2048}") int actionCapacity,
                       ApplicationEventPublisher publisher, com.wikirace.game.runtime.GameLimits limits) {
        if (actionCapacity < 1) throw new IllegalArgumentException("Action record capacity must be positive.");
        this.rooms = rooms;
        this.articles = articles;
        this.clock = clock;
        this.actionCapacity = actionCapacity;
        this.publisher = publisher::publishEvent;
        this.applicationEvents = publisher;
        this.limits = limits;
    }

    public SessionResponse create(String displayName) {
        validateName(displayName);
        String token = PlayerTokens.issue();
        var host = new PlayerRaceState(displayName, PlayerTokens.digest(token), actionCapacity);
        host.disconnectedAt(clock.instant());
        RaceRoom room = rooms.create(host);
        return inRoom(room.code(), token, (current, player) ->
                new SessionResponse(player.id(), token, view(current, player)));
    }

    public SessionResponse join(String code, String displayName) {
        validateName(displayName);
        RaceRoom room = rooms.find(code);
        String token = PlayerTokens.issue();
        var player = new PlayerRaceState(displayName, PlayerTokens.digest(token), actionCapacity);
        player.disconnectedAt(clock.instant());
        List<Object> events = new ArrayList<>();
        RoomSnapshot before = null;
        room.lock().lock();
        try {
            requireRegistered(room);
            advance(room, events);
            before = RoomSnapshotMapper.snapshot(room);
            requireWaiting(room);
            if (room.players().size() >= 4) throw error(ROOM_FULL, "Room already has four players.");
            room.addPlayer(player);
            return new SessionResponse(player.id(), token, view(room, player));
        } finally {
            captureMutation(room, before, events);
            room.lock().unlock();
            publish(events);
        }
    }

    public RoomView get(String code, String token) { return inRoom(code, token, this::view); }

    public ArticleResponse currentArticle(String code, String token) {
        return inRoom(code, token, (room, player) -> {
            if (room.status() != RaceStatus.ACTIVE && room.status() != RaceStatus.SUDDEN_DEATH && room.status() != RaceStatus.FINISHED) {
                throw error(INVALID_RACE_STATE, "Article content requires a started race.");
            }
            ArticleData article = player.article();
            return new ArticleResponse(room.code(), player.id(), room.version(),
                    player.movementRevision(), article.title(), article.html(),
                    WikiTitles.sourceUrl(article.title()), article.revisionId());
        });
    }

    private record SettingsCandidate(long version, RaceSettings settings) {}

    public RoomView configure(String code, String token, SettingsPatch patch) {
        SettingsCandidate candidate = inRoom(code, token, (room, player) -> {
            requireHost(room, player);
            requireWaiting(room);
            return new SettingsCandidate(room.version(), patch.merge(room.settings()));
        });
        RaceSettings raw = candidate.settings();
        RaceSettings canonical = new RaceSettings(resolveNullable(raw.startArticle()),
                resolveNullable(raw.targetArticle()), raw.unlimited(), raw.timeLimitSeconds());
        validateSettings(canonical, false);
        return inRoom(code, token, (room, player) -> {
            requireHost(room, player);
            requireWaiting(room);
            if (room.version() != candidate.version()) throw error(STATE_CHANGED, "Room changed; refresh and retry.");
            if (!canonical.equals(room.settings())) room.configure(canonical);
            return view(room, player);
        });
    }

    public RoomView ready(String code, String token, boolean ready) {
        return inRoom(code, token, (room, player) -> {
            requireWaiting(room);
            if (player.ready() != ready) {
                player.setReady(ready);
                room.changed();
            }
            return view(room, player);
        });
    }

    public RoomView start(String code, String token) {
        SettingsCandidate candidate = inRoom(code, token, (room, player) -> {
            requireHost(room, player);
            validateStart(room);
            return new SettingsCandidate(room.version(), room.settings());
        });
        ArticleData start = articles.loadArticle(candidate.settings().startArticle());
        return inRoom(code, token, (room, player) -> {
            requireHost(room, player);
            validateStart(room);
            if (room.version() != candidate.version()) throw error(STATE_CHANGED, "Room changed; refresh and retry.");
            room.start(clock.instant(), start);
            return view(room, player);
        });
    }

    private record PendingMove(long revision, String destination, ArticleData source, ActionResponse duplicate) {}

    public ActionResponse move(String code, String token, MoveAction action) {
        PendingMove pending = inRoom(code, token, (room, player) -> {
            ActionResponse duplicate = duplicate(room, player, action);
            if (duplicate != null) return new PendingMove(0, null, null, duplicate);
            requireMovement(room);
            player.requireMovementBudget(clock.instant(), limits.movementPerSecond());
            String destination = action.type() == MoveType.BACK ? player.history().previous() : action.destinationArticle();
            return new PendingMove(player.movementRevision(), destination, player.article(), null);
        });
        if (pending.duplicate() != null) return pending.duplicate();

        // A provider may perform slow I/O in a later phase; never hold the room lock here.
        ArticleData destination = articles.loadArticle(pending.destination());
        return inRoom(code, token, (room, player) -> {
            ActionResponse duplicate = duplicate(room, player, action);
            if (duplicate != null) return duplicate;
            requireMovement(room);
            if (player.movementRevision() != pending.revision()) {
                throw error(STATE_CHANGED, "Your location changed; refresh and retry.");
            }
            if (action.type() == MoveType.NAVIGATE && !pending.source().outgoingLinks().contains(destination.title())) {
                throw error(INVALID_NAVIGATION, "The selected article is not linked from your current article.");
            }
            player.move(action.type(), destination, room.settings().targetArticle());
            if (destination.title().equals(room.settings().targetArticle())) room.finish(player.id(), clock.instant());
            room.changed();
            player.remember(action, room.code(), room.version());
            return new ActionResponse(action.actionId(), "APPLIED", room.version(), view(room, player));
        });
    }

    private ActionResponse duplicate(RaceRoom room, PlayerRaceState player, MoveAction action) {
        var accepted = player.accepted(action.actionId());
        if (accepted == null) return null;
        if (!accepted.fingerprint().equals(action.fingerprint())) {
            throw error(ACTION_ID_CONFLICT, "This action ID was accepted with a different intent.");
        }
        return new ActionResponse(action.actionId(), "DUPLICATE", accepted.acceptedVersion(), view(room, player));
    }

    public void advanceRaces() {
        for (RaceRoom room : rooms.all()) {
            List<Object> events = new ArrayList<>();
            room.lock().lock();
            try { if (rooms.contains(room)) advance(room, events); }
            finally { room.lock().unlock(); publish(events); }
        }
    }

    public void cleanupRooms() {
        for (RaceRoom room : rooms.all()) {
            List<Object> events = new ArrayList<>();
            com.wikirace.websocket.RoomExpired expired = null;
            room.lock().lock();
            try {
                if (!rooms.contains(room)) continue;
                java.time.Instant now = clock.instant();
                boolean finished = room.status() == RaceStatus.FINISHED && !now.isBefore(room.finishedAt().plus(limits.finishedRetention()));
                var host = room.players().stream().filter(p -> p.id().equals(room.hostPlayerId())).findFirst().orElseThrow();
                boolean abandoned = room.status() == RaceStatus.WAITING && overdue(host, now);
                if (finished || abandoned) {
                    expired = new com.wikirace.websocket.RoomExpired(room.code(), room.players().stream().map(PlayerRaceState::id).toList());
                    rooms.remove(room);
                } else if (room.status() == RaceStatus.WAITING) {
                    for (PlayerRaceState player : room.players()) {
                        if (!player.id().equals(room.hostPlayerId()) && overdue(player, now)) {
                            room.removePlayer(player.id());
                            capture(room, RoomEvent.Type.ROOM_UPDATED, events);
                        }
                    }
                }
            } finally { room.lock().unlock(); publish(events); }
            if (expired != null) applicationEvents.publishEvent(expired);
        }
    }

    private boolean overdue(PlayerRaceState player, java.time.Instant now) {
        return !player.connected() && player.disconnectedAt() != null && !now.isBefore(player.disconnectedAt().plus(limits.lobbyGrace()));
    }

    private void requireRegistered(RaceRoom room) {
        if (!rooms.contains(room)) throw error(ROOM_NOT_FOUND, "Room not found.");
    }

    private <T> T inRoom(String code, String token, BiFunction<RaceRoom, PlayerRaceState, T> operation) {
        RaceRoom room = rooms.find(code);
        List<Object> events = new ArrayList<>();
        RoomSnapshot before = null;
        room.lock().lock();
        try {
            PlayerRaceState player = authenticate(room, token);
            advance(room, events);
            before = RoomSnapshotMapper.snapshot(room);
            return operation.apply(room, player);
        } finally {
            captureMutation(room, before, events);
            room.lock().unlock();
            publish(events);
        }
    }

    private void advance(RaceRoom room, List<Object> events) {
        room.advanceTime(clock.instant(), changed -> capture(changed, changed.status() == RaceStatus.ACTIVE ?
                RoomEvent.Type.RACE_STARTED : RoomEvent.Type.SUDDEN_DEATH_STARTED, events));
    }

    private void captureMutation(RaceRoom room, RoomSnapshot before, List<Object> events) {
        if (before != null && room.version() > before.version()) {
            var after = RoomSnapshotMapper.snapshot(room);
            events.add(new RoomEvent(RoomEvent.mutation(before, after), room.code(), room.version(), clock.instant(), after));
            if (before.status() != RaceStatus.FINISHED && after.status() == RaceStatus.FINISHED) {
                events.add(new com.wikirace.persistence.RaceCompleted(room.completedResult()));
            }
        }
    }

    private void capture(RaceRoom room, RoomEvent.Type type, List<Object> events) {
        events.add(new RoomEvent(type, room.code(), room.version(), clock.instant(), RoomSnapshotMapper.snapshot(room)));
    }

    private void publish(List<Object> events) {
        for (Object event : events) {
            try {
                if (event instanceof RoomEvent roomEvent) publisher.accept(roomEvent);
                else applicationEvents.publishEvent(event);
            }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(GameService.class).warn("Room event delivery failed; committed state remains available via REST.");
            }
        }
    }

    public UUID authorizeSocket(String code, String token) {
        return inRoom(code, token, (room, player) -> player.id());
    }

    public void requireSocketMember(String code, UUID playerId) {
        RaceRoom room = rooms.find(code);
        room.lock().lock();
        try { requireRegistered(room); socketPlayer(room, playerId); }
        finally { room.lock().unlock(); }
    }

    public void socketPresence(String code, UUID playerId, String sessionId, boolean connected) {
        RaceRoom room = rooms.find(code);
        List<Object> events = new ArrayList<>();
        room.lock().lock();
        try {
            requireRegistered(room);
            advance(room, events);
            PlayerRaceState player = socketPlayer(room, playerId);
            boolean wasConnected = player.connected();
            if (connected) player.connect(sessionId); else player.disconnect(sessionId);
            if (wasConnected != player.connected()) {
                player.disconnectedAt(player.connected() ? null : clock.instant());
                room.changed();
                capture(room, player.connected() ? RoomEvent.Type.PLAYER_RECONNECTED : RoomEvent.Type.PLAYER_DISCONNECTED, events);
            }
        } finally { room.lock().unlock(); publish(events); }
    }

    private PlayerRaceState socketPlayer(RaceRoom room, UUID playerId) {
        return room.players().stream().filter(player -> player.id().equals(playerId)).findFirst()
                .orElseThrow(() -> error(INVALID_PLAYER_TOKEN, "Invalid player session."));
    }
    public java.util.Optional<com.wikirace.dto.RaceResults> completedResult(UUID id) {
        for (RaceRoom room : rooms.all()) {
            if (!room.id().equals(id)) continue;
            room.lock().lock();
            try {
                if (!rooms.contains(room)) continue;
                if (room.status() != RaceStatus.FINISHED) throw error(RACE_NOT_FINISHED, "Race is not finished.");
                return java.util.Optional.of(room.completedResult());
            } finally { room.lock().unlock(); }
        }
        return java.util.Optional.empty();
    }

    public void persistenceStatus(UUID id, String status) {
        for (RaceRoom room : rooms.all()) {
            if (!room.id().equals(id)) continue;
            List<Object> events = new ArrayList<>();
            room.lock().lock();
            try {
                if (rooms.contains(room) && "PENDING".equals(room.resultPersistenceStatus())) {
                    room.resultPersistenceStatus(status);
                    capture(room, RoomEvent.Type.ROOM_UPDATED, events);
                }
            } finally { room.lock().unlock(); publish(events); }
            return;
        }
    }

    private PlayerRaceState authenticate(RaceRoom room, String token) {
        requireRegistered(room);
        if (token == null || token.isBlank()) throw error(INVALID_PLAYER_TOKEN, "Invalid player token.");
        byte[] digest = PlayerTokens.digest(token);
        return room.players().stream().filter(player -> MessageDigest.isEqual(digest, player.tokenDigest()))
                .findFirst().orElseThrow(() -> error(INVALID_PLAYER_TOKEN, "Invalid player token."));
    }

    private RoomView view(RaceRoom room, PlayerRaceState player) {
        return RoomSnapshotMapper.view(room, player, clock.instant());
    }

    private static void validateName(String name) {
        if (name == null || name.codePointCount(0, name.length()) < 2 || name.codePointCount(0, name.length()) > 20 ||
                !NAME.matcher(name).matches()) throw error(INVALID_DISPLAY_NAME, "Display name must contain 2-20 supported characters.");
    }

    private String resolveNullable(String title) { return title == null ? null : articles.resolve(title); }

    private static void validateSettings(RaceSettings settings, boolean complete) {
        if ((complete && (settings.startArticle() == null || settings.targetArticle() == null)) ||
                (settings.startArticle() != null && settings.startArticle().equals(settings.targetArticle())) ||
                (settings.unlimited() ? settings.timeLimitSeconds() != null :
                        settings.timeLimitSeconds() == null || settings.timeLimitSeconds() < 180)) {
            throw error(INVALID_ROOM_SETTINGS, "Choose distinct valid articles and a timed duration of at least 180 seconds, or unlimited.");
        }
    }

    private static void validateStart(RaceRoom room) {
        requireWaiting(room);
        validateSettings(room.settings(), true);
        if (room.players().size() < 2 || room.players().size() > 4 || room.players().stream().anyMatch(player -> !player.ready())) {
            throw error(PLAYERS_NOT_READY, "Start requires 2-4 players and everyone ready.");
        }
    }

    private static void requireWaiting(RaceRoom room) {
        if (room.status() != RaceStatus.WAITING) throw error(INVALID_RACE_STATE, "This command requires a waiting room.");
    }

    private static void requireHost(RaceRoom room, PlayerRaceState player) {
        if (!room.hostPlayerId().equals(player.id())) throw error(NOT_HOST, "Only the host may perform this command.");
    }

    private static void requireMovement(RaceRoom room) {
        if (room.status() == RaceStatus.FINISHED) throw error(RACE_FINISHED, "The race has finished.");
        if (room.status() != RaceStatus.ACTIVE && room.status() != RaceStatus.SUDDEN_DEATH) {
            throw error(INVALID_RACE_STATE, "Movement requires an active race.");
        }
    }

    private static GameException error(GameException.Code code, String message) { return new GameException(code, message); }
}
