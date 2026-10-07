package com.wikirace.websocket;
import java.security.Principal;
import java.util.UUID;
public record RoomPrincipal(String roomCode, UUID playerId) implements Principal {
    public String getName() { return roomCode + ":" + playerId; }
}
