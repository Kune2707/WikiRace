package com.wikirace.game.runtime;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.wikirace.exception.GameException;
import com.wikirace.game.model.PlayerRaceState;
import com.wikirace.game.model.RaceRoom;
import static com.wikirace.exception.GameException.Code.ROOM_NOT_FOUND;

@Component
public class RoomRegistry {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, RaceRoom> rooms = new ConcurrentHashMap<>();

    public RaceRoom create(PlayerRaceState host) {
        while (true) {
            StringBuilder code = new StringBuilder(6);
            for (int i = 0; i < 6; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            RaceRoom room = new RaceRoom(code.toString(), host);
            if (rooms.putIfAbsent(room.code(), room) == null) return room;
        }
    }

    public RaceRoom find(String code) {
        RaceRoom room = code == null ? null : rooms.get(code);
        if (room == null) throw new GameException(ROOM_NOT_FOUND, "Room not found.");
        return room;
    }

    public Collection<RaceRoom> all() { return List.copyOf(rooms.values()); }
    public boolean contains(RaceRoom room) { return rooms.get(room.code()) == room; }
    public boolean remove(RaceRoom room) { return rooms.remove(room.code(), room); }
}
