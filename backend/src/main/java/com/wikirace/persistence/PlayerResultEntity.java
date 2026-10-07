package com.wikirace.persistence;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import com.wikirace.dto.RaceResults.PlayerResult;

@Entity
@Table(name = "player_result")
public class PlayerResultEntity {
    @EmbeddedId PlayerResultId id;
    @MapsId("raceId") @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "race_id") CompletedRaceEntity race;
    @Column(name = "player_index", nullable = false) int playerIndex;
    @Column(name = "display_name", columnDefinition = "text", nullable = false) String displayName;
    @Column(name = "click_count", nullable = false) int clickCount;
    @Column(name = "final_article", columnDefinition = "text", nullable = false) String finalArticle;
    @Column(nullable = false) boolean winner;
    @ElementCollection
    @CollectionTable(name = "visit_step", joinColumns = {@JoinColumn(name = "race_id", referencedColumnName = "race_id"),
            @JoinColumn(name = "player_id", referencedColumnName = "player_id")})
    @OrderColumn(name = "step_index")
    @Column(name = "article_title", columnDefinition = "text", nullable = false)
    List<String> visitLog = new ArrayList<>();
    protected PlayerResultEntity() {}
    PlayerResultEntity(CompletedRaceEntity race, PlayerResult result, int index) {
        this.race = race; id = new PlayerResultId(race.id, result.playerId()); playerIndex = index;
        displayName = result.displayName(); clickCount = result.clickCount(); finalArticle = result.finalArticle();
        winner = result.winner(); visitLog = new ArrayList<>(result.visitLog());
    }
    PlayerResult result() { return new PlayerResult(id.playerId, displayName, clickCount, finalArticle, winner, visitLog); }
}
