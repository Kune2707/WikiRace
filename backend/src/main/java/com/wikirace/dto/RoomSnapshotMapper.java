package com.wikirace.dto;

import java.time.Instant;
import com.wikirace.game.model.PlayerRaceState;
import com.wikirace.game.model.RaceRoom;
import com.wikirace.game.model.RaceStatus;
import static com.wikirace.dto.GameDtos.*;

public final class RoomSnapshotMapper {
    private RoomSnapshotMapper() {}

    public static RoomView view(RaceRoom room, PlayerRaceState me, Instant now) {
        boolean visible = room.status() != RaceStatus.WAITING && room.status() != RaceStatus.COUNTDOWN;
        var privateView = new PrivatePlayer(me.id(), visible ? me.article().title() : null, me.clickCount(),
                visible && me.history().canGoBack(), me.movementRevision());
        return new RoomView(now, snapshot(room), privateView);
    }

    public static RoomSnapshot snapshot(RaceRoom room) {
        boolean visible = room.status() != RaceStatus.WAITING && room.status() != RaceStatus.COUNTDOWN;
        var players = room.players().stream().map(player -> new PublicPlayer(player.id(), player.displayName(),
                player.id().equals(room.hostPlayerId()), player.ready(), player.connected(), player.clickCount(),
                visible && player.closeToTarget(),
                visible && !player.closeToTarget() ? player.article().title() : null)).toList();
        return new RoomSnapshot(room.code(), room.version(), room.status(), room.hostPlayerId(),
                room.settings(), players, room.startsAt(), room.suddenDeathStartedAt(), room.finishedAt(),
                room.winnerPlayerId(), room.status() == RaceStatus.FINISHED ? room.id() : null, room.resultPersistenceStatus());
    }
}
