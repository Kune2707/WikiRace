package com.wikirace.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.wikirace.game.service.GameService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// The production transport configuration uses the offline provider and no database here.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CORS_ALLOWED_ORIGINS=https://wikirace.example",
        "WEBSOCKET_ALLOWED_ORIGINS=https://wikirace.example",
        "DATABASE_URL=jdbc:postgresql://localhost:5432/unused",
        "DATABASE_USER=unused",
        "DATABASE_PASSWORD=offline-test-only",
        "WIKIPEDIA_USER_AGENT=WikiRace/0.0.1 (offline configuration test)"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "production"})
@Timeout(15)
class ProductionConfigurationTest {
    @Autowired MockMvc mvc;
    @Autowired GameService games;
    @LocalServerPort int port;

    @Test void healthRemainsPublicForProviderChecks() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\"}"));
    }

    @Test void configuredHttpsOriginCanReadHealth() throws Exception {
        mvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, "https://wikirace.example"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://wikirace.example"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test void authenticatedPatchPreflightAllowsRequiredHeaders() throws Exception {
        mvc.perform(options("/api/rooms/ABC234/settings")
                        .header(HttpHeaders.ORIGIN, "https://wikirace.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-player-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://wikirace.example"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type, x-player-token"));
    }

    @Test void unconfiguredPreviewAndLocalOriginsAreRejected() throws Exception {
        for (String origin : new String[]{"https://unexpected-preview.vercel.app", "http://localhost:5173"}) {
            mvc.perform(options("/api/rooms")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Test void productionOriginCanAuthenticateNativeStomp() throws Exception {
        var host = games.create("Production Test");
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        var socket = connect("https://wikirace.example", new WebSocket.Listener() {
            final StringBuilder frame = new StringBuilder();
            public void onOpen(WebSocket socket) { socket.request(1); }
            public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                frame.append(data);
                if (last) { frames.add(frame.toString()); frame.setLength(0); }
                socket.request(1);
                return null;
            }
        });
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nheart-beat:0,0\nroomCode:"
                    + host.view().room().roomCode() + "\nX-Player-Token:"
                    + host.playerSessionToken() + "\n\n\0", true).join();
            assertThat(frames.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED")
                    .doesNotContain(host.playerSessionToken());
        } finally { socket.abort(); }
    }

    @Test void unconfiguredWebSocketOriginIsRejected() {
        assertThatThrownBy(() -> connect("https://unexpected-preview.vercel.app", new WebSocket.Listener() {}))
                .isInstanceOf(ExecutionException.class);
    }

    private WebSocket connect(String origin, WebSocket.Listener listener) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", origin)
                .subprotocols("v12.stomp")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), listener)
                .get(5, TimeUnit.SECONDS);
    }
}
