package com.wikirace.websocket;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import com.wikirace.game.service.GameService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="app.websocket.allowed-origins=http://localhost")
@ActiveProfiles("test")
@Timeout(15)
class StompIntegrationTest {
    @LocalServerPort int port;
    @Autowired GameService games;
    WebSocketStompClient client;
    ThreadPoolTaskScheduler scheduler;
    final List<StompSession> sessions = new ArrayList<>();
    @BeforeEach void setup() {
        scheduler = new ThreadPoolTaskScheduler(); scheduler.initialize();
        client = new WebSocketStompClient(new StandardWebSocketClient()); client.setTaskScheduler(scheduler);
        var converter = new MappingJackson2MessageConverter(); converter.setObjectMapper(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        client.setDefaultHeartbeat(new long[]{0,0}); client.setMessageConverter(converter); client.setReceiptTimeLimit(3000);
    }
    @AfterEach void stop() { sessions.forEach(session -> { if (session.isConnected()) session.disconnect(); }); client.stop(); scheduler.shutdown(); }
    StompSession connect(String code,String token) throws Exception {
        var http = new WebSocketHttpHeaders(); http.setOrigin("http://localhost");
        var headers = new StompHeaders(); headers.set("roomCode",code); headers.set("X-Player-Token",token);
        var session = client.connectAsync("ws://localhost:"+port+"/ws",http,headers,new StompSessionHandlerAdapter() {}).get(5,TimeUnit.SECONDS);
        sessions.add(session); session.setAutoReceipt(true); return session;
    }
    BlockingQueue<RoomEvent> subscribe(StompSession session,String code) throws Exception {
        var queue = new LinkedBlockingQueue<RoomEvent>(); var receipt = new CompletableFuture<Void>();
        var subscription = session.subscribe("/topic/rooms/"+code,new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return RoomEvent.class; }
            public void handleFrame(StompHeaders headers,Object payload) { queue.add((RoomEvent)payload); }
        });
        subscription.addReceiptTask(() -> receipt.complete(null)); subscription.addReceiptLostTask(() -> receipt.completeExceptionally(new AssertionError("Missing subscription receipt")));
        receipt.get(5,TimeUnit.SECONDS); return queue;
    }
    @Test void subscriptionsAreEstablishedBeforeReceiptAndReceiveOnlyTheirOwnRoom() throws Exception {
        var a = games.create("Host A"); var b = games.create("Host B");
        String ac = a.view().room().roomCode(); String bc = b.view().room().roomCode();
        var aq = subscribe(connect(ac,a.playerSessionToken()),ac); var bq = subscribe(connect(bc,b.playerSessionToken()),bc);
        games.ready(ac,a.playerSessionToken(),true);
        RoomEvent received = aq.poll(3,TimeUnit.SECONDS); assertThat(received).isNotNull();
        assertThat(received.roomCode()).isEqualTo(ac); assertThat(received.type()).isEqualTo(RoomEvent.Type.PLAYER_READY_CHANGED);
        assertThat(bq.poll(200,TimeUnit.MILLISECONDS)).isNull();
        assertThat(received.room().players().getFirst().connected()).isTrue();
    }
    @Test void finalSocketDisconnectChangesPresenceWithoutRemovingPlayerAndReconnectRestoresIdentity() throws Exception {
        var host = games.create("Host"); String code = host.view().room().roomCode();
        var first = connect(code,host.playerSessionToken()); subscribe(first,code);
        var second = connect(code,host.playerSessionToken()); subscribe(second,code);
        first.disconnect();
        assertThat(games.get(code,host.playerSessionToken()).room().players().getFirst().connected()).isTrue();
        second.disconnect();
        long until = System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while (games.get(code,host.playerSessionToken()).room().players().getFirst().connected() && System.nanoTime()<until) Thread.sleep(20);
        assertThat(games.get(code,host.playerSessionToken()).room().players().getFirst().connected()).isFalse();
        subscribe(connect(code,host.playerSessionToken()),code);
        assertThat(games.get(code,host.playerSessionToken()).me().playerId()).isEqualTo(host.playerId());
        assertThat(games.get(code,host.playerSessionToken()).room().players()).hasSize(1);
    }

    static class RawFrames implements java.net.http.WebSocket.Listener {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final StringBuilder text = new StringBuilder();
        public void onOpen(java.net.http.WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(java.net.http.WebSocket socket, CharSequence data, boolean last) {
            text.append(data); if (last) { frames.add(text.toString()); text.setLength(0); }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        String next() throws InterruptedException { return frames.poll(3,TimeUnit.SECONDS); }
    }
    java.net.http.WebSocket raw(RawFrames frames,String origin,String path) throws Exception {
        return java.net.http.HttpClient.newHttpClient().newWebSocketBuilder().header("Origin",origin).subprotocols("v12.stomp")
                .buildAsync(java.net.URI.create("ws://localhost:"+port+path),frames).get(5,TimeUnit.SECONDS);
    }
    @Test void wrongRoomTokenIsRejectedWithoutEchoingTheSecret() throws Exception {
        var host = games.create("Host"); var other = games.create("Other"); var frames = new RawFrames();
        var socket = raw(frames,"http://localhost","/ws");
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nroomCode:"+host.view().room().roomCode()+"\nX-Player-Token:"+other.playerSessionToken()+"\n\n\0",true).join();
            String error = frames.next(); assertThat(error).startsWith("ERROR").doesNotContain(other.playerSessionToken());
        } finally { socket.abort(); }
    }
    @Test void clientCannotSubscribeToAnotherRoomOrForgeServerSnapshots() throws Exception {
        var host = games.create("Host"); String code = host.view().room().roomCode();
        for (String command : List.of("SUBSCRIBE\nid:forbidden\ndestination:/topic/rooms/**\n\n\0", "SEND\ndestination:/topic/rooms/"+code+"\n\n{\"status\":\"FINISHED\"}\0")) {
            var frames = new RawFrames(); var socket = raw(frames,"http://localhost","/ws");
            try {
                socket.sendText("CONNECT\naccept-version:1.2\nroomCode:"+code+"\nX-Player-Token:"+host.playerSessionToken()+"\n\n\0",true).join();
                assertThat(frames.next()).startsWith("CONNECTED"); socket.sendText(command,true).join();
                assertThat(frames.next()).startsWith("ERROR").doesNotContain(host.playerSessionToken());
                assertThat(games.get(code,host.playerSessionToken()).room().status()).isEqualTo(com.wikirace.game.model.RaceStatus.WAITING);
            } finally { socket.abort(); }
        }
    }
    @Test void disallowedOriginAndQueryStringHandshakeAreRejected() {
        assertThatThrownBy(() -> raw(new RawFrames(),"https://evil.example","/ws")).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> raw(new RawFrames(),"http://localhost","/ws?token=forbidden")).isInstanceOf(ExecutionException.class);
    }
}
