package com.wikirace.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;
import com.wikirace.dto.RaceResults;

@Entity
@Table(name = "completed_race")
public class CompletedRaceEntity {
    @Id UUID id;
    @Column(name = "room_code", length = 6, nullable = false) String roomCode;
    @Column(name = "start_article", columnDefinition = "text", nullable = false) String startArticle;
    @Column(name = "target_article", columnDefinition = "text", nullable = false) String targetArticle;
    @Column(nullable = false) boolean unlimited;
    @Column(name = "time_limit_seconds") Integer timeLimitSeconds;
    @Column(name = "started_at", nullable = false) Instant startedAt;
    @Column(name = "sudden_death_started_at") Instant suddenDeathStartedAt;
    @Column(name = "finished_at", nullable = false) Instant finishedAt;
    @Column(name = "winner_player_id", nullable = false) UUID winnerPlayerId;
    @OneToMany(mappedBy = "race") @OrderBy("playerIndex ASC")
    List<PlayerResultEntity> players = new ArrayList<>();
    protected CompletedRaceEntity() {}
    RaceResults result() {
        return new RaceResults(id, roomCode, startArticle, targetArticle, unlimited, timeLimitSeconds, startedAt,
                suddenDeathStartedAt, finishedAt, Duration.between(startedAt, finishedAt).getSeconds(), winnerPlayerId,
                players.stream().map(PlayerResultEntity::result).toList());
    }
}
