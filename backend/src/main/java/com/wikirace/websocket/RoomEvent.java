package com.wikirace.websocket;

import java.time.Instant;
import com.wikirace.dto.GameDtos.RoomSnapshot;

public record RoomEvent(Type type, String roomCode, long version, Instant occurredAt, RoomSnapshot room) {
    public enum Type { ROOM_UPDATED, PLAYER_JOINED, PLAYER_READY_CHANGED, SETTINGS_UPDATED, COUNTDOWN_STARTED,
        RACE_STARTED, PLAYER_MOVED, PLAYER_CLOSE_CHANGED, PLAYER_DISCONNECTED, PLAYER_RECONNECTED,
        SUDDEN_DEATH_STARTED, RACE_FINISHED }

    public static Type mutation(RoomSnapshot before, RoomSnapshot after) {
        if (before.status() != after.status()) return switch (after.status()) {
            case COUNTDOWN -> Type.COUNTDOWN_STARTED;
            case ACTIVE -> Type.RACE_STARTED;
            case SUDDEN_DEATH -> Type.SUDDEN_DEATH_STARTED;
            case FINISHED -> Type.RACE_FINISHED;
            default -> Type.ROOM_UPDATED;
        };
        if (after.players().size() > before.players().size()) return Type.PLAYER_JOINED;
        if (!before.settings().equals(after.settings())) return Type.SETTINGS_UPDATED;
        for (int i = 0; i < Math.min(before.players().size(), after.players().size()); i++) {
            var old = before.players().get(i); var player = after.players().get(i);
            if (old.connected() != player.connected()) return player.connected() ? Type.PLAYER_RECONNECTED : Type.PLAYER_DISCONNECTED;
            if (old.ready() != player.ready()) return Type.PLAYER_READY_CHANGED;
            if (old.clickCount() != player.clickCount()) return old.closeToTarget() != player.closeToTarget() ? Type.PLAYER_CLOSE_CHANGED : Type.PLAYER_MOVED;
        }
        return Type.ROOM_UPDATED;
    }
}
