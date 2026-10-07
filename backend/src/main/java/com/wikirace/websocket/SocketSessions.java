package com.wikirace.websocket;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Component
public class SocketSessions {
    private final ConcurrentHashMap<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sockets.put(session.getId(), session);
                try { super.afterConnectionEstablished(session); }
                catch (Exception failure) { sockets.remove(session.getId()); throw failure; }
            }
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sockets.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }
    public void close(String id) {
        WebSocketSession socket = sockets.remove(id);
        if (socket != null) {
            try { socket.close(new CloseStatus(1008, "Room expired.")); }
            catch (IOException ignored) { /* Already disconnected. */ }
        }
    }
}
