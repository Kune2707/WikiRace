package com.wikirace.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.wikirace.game.model.RaceRoom;

public record RaceResults(UUID id, String roomCode, String startArticle, String targetArticle,
                          boolean unlimited, Integer timeLimitSeconds, Instant startedAt,
                          Instant suddenDeathStartedAt, Instant finishedAt, long elapsedSeconds,
                          UUID winnerPlayerId, List<PlayerResult> players) {
    public RaceResults { players = List.copyOf(players); }
    public record PlayerResult(UUID playerId, String displayName, int clickCount, String finalArticle,
                               boolean winner, List<String> visitLog) {
        public PlayerResult { visitLog = List.copyOf(visitLog); }
    }
    public static RaceResults capture(RaceRoom room) {
        if (room.finishedAt() == null) throw new IllegalStateException("Only completed races have results.");
        var settings = room.settings();
        // PostgreSQL timestamptz retains microseconds, not the runtime clock's nanoseconds.
        var start = room.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var finish = room.finishedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var sudden = room.suddenDeathStartedAt() == null ? null : room.suddenDeathStartedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return new RaceResults(room.id(), room.code(), settings.startArticle(), settings.targetArticle(),
                settings.unlimited(), settings.timeLimitSeconds(), start, sudden,
                finish, Duration.between(start, finish).getSeconds(), room.winnerPlayerId(),
                room.players().stream().map(player -> new PlayerResult(player.id(), player.displayName(), player.clickCount(),
                        player.article().title(), player.id().equals(room.winnerPlayerId()), player.visitLog())).toList());
    }
}
