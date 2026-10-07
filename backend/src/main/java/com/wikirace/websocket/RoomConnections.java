package com.wikirace.websocket;

import java.util.HashMap;
import java.util.Map;
import com.wikirace.game.service.GameService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class RoomConnections {
    private final Map<String, RoomPrincipal> sessions = new HashMap<>();
    private final GameService games;
    private final SocketSessions sockets;
    public RoomConnections(GameService games, SocketSessions sockets) { this.games = games; this.sockets = sockets; }

    @EventListener public synchronized void connected(SessionConnectedEvent event) {
        if (!(event.getUser() instanceof RoomPrincipal player)) return;
        String session = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (session != null && sessions.putIfAbsent(session, player) == null) {
            try { games.socketPresence(player.roomCode(), player.playerId(), session, true); }
            catch (com.wikirace.exception.GameException expired) { sessions.remove(session); sockets.close(session); }
        }
    }
    @EventListener public synchronized void disconnected(SessionDisconnectEvent event) {
        RoomPrincipal player = sessions.remove(event.getSessionId());
        if (player != null) {
            try { games.socketPresence(player.roomCode(), player.playerId(), event.getSessionId(), false); }
            catch (com.wikirace.exception.GameException expired) { /* Cleanup already removed this room or player. */ }
        }
    }
    @EventListener public synchronized void expired(RoomExpired event) {
        var ids = sessions.entrySet().stream().filter(entry -> entry.getValue().roomCode().equals(event.roomCode()) &&
                event.players().contains(entry.getValue().playerId())).map(Map.Entry::getKey).toList();
        for (String id : ids) { sessions.remove(id); sockets.close(id); }
    }
}
