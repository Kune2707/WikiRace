package com.wikirace.websocket;

import java.util.List;
import java.util.UUID;

public record RoomExpired(String roomCode, List<UUID> players) {}
