package com.wikirace.websocket;

import com.wikirace.game.service.GameService;
import org.springframework.messaging.*;
import org.springframework.messaging.support.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;

@Component
public class StompGuard implements ChannelInterceptor {
    private final GameService games;
    public StompGuard(GameService games) { this.games = games; }

    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw denied();
        var tokens = headers.getNativeHeader("X-Player-Token");
        if (tokens != null) headers.removeNativeHeader("X-Player-Token");
        StompCommand command = headers.getCommand();
        if (command == null) return message; // Transport heartbeats have no command or destination.
        if (command == StompCommand.DISCONNECT) return message;
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            if (headers.getUser() != null) throw denied();
            String code = single(headers, "roomCode");
            if (tokens == null || tokens.size() != 1 || tokens.getFirst() == null || tokens.getFirst().isBlank()) throw denied();
            String token = tokens.getFirst();
            if (!code.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}")) throw denied();
            try { headers.setUser(new RoomPrincipal(code, games.authorizeSocket(code, token))); }
            catch (RuntimeException failure) { throw denied(); }
            return message;
        }
        if (!(headers.getUser() instanceof RoomPrincipal player)) throw denied();
        if (command == StompCommand.SUBSCRIBE) {
            if (!("/topic/rooms/" + player.roomCode()).equals(headers.getDestination())) throw denied();
            games.requireSocketMember(player.roomCode(), player.playerId());
        } else if (command != StompCommand.UNSUBSCRIBE && command != StompCommand.DISCONNECT) throw denied();
        return message;
    }
    private static String single(StompHeaderAccessor headers, String name) {
        var values = headers.getNativeHeader(name);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) throw denied();
        return values.getFirst();
    }
    private static MessageDeliveryException denied() { return new MessageDeliveryException("Unauthorized STOMP operation."); }
}
