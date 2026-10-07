package com.wikirace.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.wikirace.game.model.RaceSettings;
import com.wikirace.game.model.RaceStatus;

public final class GameDtos {
    private GameDtos() {}
    public record PublicPlayer(UUID playerId, String displayName, boolean host, boolean ready, boolean connected,
                               int clickCount, boolean closeToTarget, String currentArticle) {}
    public record RoomSnapshot(String roomCode, long version, RaceStatus status, UUID hostPlayerId,
                               RaceSettings settings, List<PublicPlayer> players, Instant startsAt,
                               Instant suddenDeathStartedAt, Instant finishedAt, UUID winnerPlayerId,
                               UUID resultId, String resultPersistenceStatus) {}
    public record PrivatePlayer(UUID playerId, String currentArticle, int clickCount, boolean canGoBack,
                                long movementRevision) {}
    public record RoomView(Instant serverTime, RoomSnapshot room, PrivatePlayer me) {}
    public record SessionResponse(UUID playerId, String playerSessionToken, RoomView view) {}
    public record ActionResponse(UUID actionId, String outcome, long appliedVersion, RoomView view) {}
}
