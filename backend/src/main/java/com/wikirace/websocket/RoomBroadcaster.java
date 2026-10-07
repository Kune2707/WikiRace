package com.wikirace.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoomBroadcaster {
    private final SimpMessagingTemplate messages;
    public RoomBroadcaster(SimpMessagingTemplate messages) { this.messages = messages; }
    @EventListener public void broadcast(RoomEvent event) {
        messages.convertAndSend("/topic/rooms/" + event.roomCode(), event);
    }
}
