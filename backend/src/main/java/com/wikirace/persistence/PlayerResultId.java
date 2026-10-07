package com.wikirace.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.*;

@Embeddable
public class PlayerResultId implements Serializable {
    @Column(name = "race_id") UUID raceId;
    @Column(name = "player_id") UUID playerId;
    protected PlayerResultId() {}
    PlayerResultId(UUID raceId, UUID playerId) { this.raceId = raceId; this.playerId = playerId; }
    public boolean equals(Object other) {
        return other instanceof PlayerResultId id && Objects.equals(raceId, id.raceId) && Objects.equals(playerId, id.playerId);
    }
    public int hashCode() { return Objects.hash(raceId, playerId); }
}
