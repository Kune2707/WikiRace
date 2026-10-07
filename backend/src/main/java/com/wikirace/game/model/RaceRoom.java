package com.wikirace.game.model;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import com.wikirace.wikipedia.ArticleData;

// Mutable state is accessed only while holding this room's lock.
public class RaceRoom {
    private final UUID id = UUID.randomUUID();
    private final String code;
    private final UUID hostPlayerId;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<UUID, PlayerRaceState> players = new LinkedHashMap<>();
    private RaceSettings settings = RaceSettings.unconfigured();
    private RaceStatus status = RaceStatus.WAITING;
    private long version = 1;
    private Instant startsAt;
    private Instant suddenDeathStartedAt;
    private Instant finishedAt;
    private UUID winnerPlayerId;
    private com.wikirace.dto.RaceResults completedResult;
    private String resultPersistenceStatus;
    public com.wikirace.dto.RaceResults completedResult() { return completedResult; }
    public String resultPersistenceStatus() { return resultPersistenceStatus; }
    public void resultPersistenceStatus(String status) { resultPersistenceStatus = status; changed(); }

    public RaceRoom(String code, PlayerRaceState host) {
        this.code = code;
        hostPlayerId = host.id();
        players.put(host.id(), host);
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public UUID hostPlayerId() { return hostPlayerId; }
    public ReentrantLock lock() { return lock; }
    public Collection<PlayerRaceState> players() { return List.copyOf(players.values()); }
    public RaceSettings settings() { return settings; }
    public RaceStatus status() { return status; }
    public long version() { return version; }
    public Instant startsAt() { return startsAt; }
    public Instant suddenDeathStartedAt() { return suddenDeathStartedAt; }
    public Instant finishedAt() { return finishedAt; }
    public UUID winnerPlayerId() { return winnerPlayerId; }
    public void changed() { version++; }
    public void addPlayer(PlayerRaceState player) { players.put(player.id(), player); changed(); }
    public void removePlayer(UUID id) { if (players.remove(id) != null) changed(); }

    public void configure(RaceSettings settings) {
        this.settings = settings;
        players.values().forEach(player -> player.setReady(false));
        changed();
    }

    public void start(Instant now, ArticleData start) {
        startsAt = now.plusSeconds(3);
        status = RaceStatus.COUNTDOWN;
        players.values().forEach(player -> player.initialize(start, settings.targetArticle()));
        changed();
    }

    public void advanceTime(Instant now) {
        advanceTime(now, room -> {});
    }

    public void advanceTime(Instant now, java.util.function.Consumer<RaceRoom> transitioned) {
        if (status == RaceStatus.COUNTDOWN && !now.isBefore(startsAt)) {
            status = RaceStatus.ACTIVE;
            changed();
            transitioned.accept(this);
        }
        if (status == RaceStatus.ACTIVE && !settings.unlimited()) {
            Instant deadline = startsAt.plusSeconds(settings.timeLimitSeconds());
            if (!now.isBefore(deadline)) {
                status = RaceStatus.SUDDEN_DEATH;
                suddenDeathStartedAt = deadline;
                changed();
                transitioned.accept(this);
            }
        }
    }

    public void finish(UUID playerId, Instant now) {
        winnerPlayerId = playerId;
        finishedAt = now;
        status = RaceStatus.FINISHED;
        completedResult = com.wikirace.dto.RaceResults.capture(this);
        resultPersistenceStatus = "PENDING";
    }
}
