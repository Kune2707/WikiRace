package com.wikirace.persistence;

import java.util.UUID;
import com.wikirace.dto.RaceResults;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

@Profile("!test | persistence-test")
public interface CompletedRaceRepository extends JpaRepository<CompletedRaceEntity, UUID> {
    // Claim the immutable aggregate once; concurrent duplicate saves wait for the first transaction.
    @Modifying
    @Query(value = """
        INSERT INTO completed_race (id, room_code, start_article, target_article, unlimited, time_limit_seconds,
            started_at, sudden_death_started_at, finished_at, winner_player_id)
        VALUES (:#{#race.id()}, :#{#race.roomCode()}, :#{#race.startArticle()}, :#{#race.targetArticle()},
            :#{#race.unlimited()}, :#{#race.timeLimitSeconds()}, :#{#race.startedAt()},
            :#{#race.suddenDeathStartedAt()}, :#{#race.finishedAt()}, :#{#race.winnerPlayerId()})
        ON CONFLICT (id) DO NOTHING
        """, nativeQuery = true)
    int claim(@Param("race") RaceResults race);
}
