package com.wikirace.websocket;

import java.time.*;
import java.util.concurrent.*;
import com.wikirace.game.service.GameService;
import com.wikirace.game.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.websocket.allowed-origins=http://localhost", "app.game.tick-millis=600000", "app.game.cleanup-millis=600000"})
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(RoomCleanupSocketTest.Config.class)
class RoomCleanupSocketTest {
    @TestConfiguration static class Config { @Bean @Primary TestClock clock() { return new TestClock(); } }
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    static class Frames extends StompIntegrationTest.RawFrames {
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        public CompletionStage<?> onClose(java.net.http.WebSocket socket, int code, String reason) {
            closed.complete(code); return CompletableFuture.completedFuture(null);
        }
    }
    @Autowired GameService games;
    @Autowired TestClock clock;
    @LocalServerPort int port;
    @Test void expiredHostLobbyClosesRemainingGuestSocketAndRevokesRoom() throws Exception {
        var host = games.create("Host"); String code = host.view().room().roomCode(); var guest = games.join(code, "Guest");
        var frames = new Frames();
        var socket = java.net.http.HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", "http://localhost")
                .subprotocols("v12.stomp").buildAsync(java.net.URI.create("ws://localhost:" + port + "/ws"), frames).get(3, TimeUnit.SECONDS);
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nroomCode:" + code + "\nX-Player-Token:" + guest.playerSessionToken() + "\n\n\0", true).join();
            assertThat(frames.next()).startsWith("CONNECTED");
            socket.sendText("SUBSCRIBE\nid:room\ndestination:/topic/rooms/" + code + "\nreceipt:sub\n\n\0", true).join();
            assertThat(frames.next()).startsWith("RECEIPT");
            clock.now = clock.now.plusSeconds(300); games.cleanupRooms();
            assertThat(frames.closed.get(3, TimeUnit.SECONDS)).isEqualTo(1008);
            assertThatThrownBy(() -> games.get(code, guest.playerSessionToken())).isInstanceOf(com.wikirace.exception.GameException.class);
        } finally { socket.abort(); }
    }
}
