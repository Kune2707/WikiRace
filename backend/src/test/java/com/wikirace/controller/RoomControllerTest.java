package com.wikirace.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.game.tick-millis=60000")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Import(RoomControllerTest.TestConfig.class)
class RoomControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TestClock clock;

    static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T00:00:00Z"));
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        public Instant instant() { return now.get(); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }

    private JsonNode request(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(status().is(expected))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode create() throws Exception {
        return request(post("/api/rooms").content("{\"displayName\":\"Host\"}"), 201);
    }

    private String token(JsonNode session) { return session.get("playerSessionToken").asText(); }
    private String code(JsonNode session) { return session.at("/view/room/roomCode").asText(); }
    private String base(JsonNode session) { return "/api/rooms/" + code(session); }

    private JsonNode active() throws Exception {
        var host = create();
        var guest = request(post(base(host) + "/join").content("{\"displayName\":\"Guest\"}"), 201);
        request(patch(base(host) + "/settings").header("X-Player-Token", token(host)).content(
                "{\"startArticle\":\"Computer Science\",\"targetArticle\":\"Quantum Mechanics\",\"unlimited\":true,\"timeLimitSeconds\":null}"), 200);
        request(post(base(host) + "/ready").header("X-Player-Token", token(host)).content("{\"ready\":true}"), 200);
        request(post(base(host) + "/ready").header("X-Player-Token", token(guest)).content("{\"ready\":true}"), 200);
        var countdown = request(post(base(host) + "/start").header("X-Player-Token", token(host)).content("{}"), 200);
        assertThat(countdown.at("/room/status").asText()).isEqualTo("COUNTDOWN");
        assertThat(countdown.at("/me/currentArticle").isNull()).isTrue();
        clock.advance(3);
        return host;
    }

    private MockHttpServletRequestBuilder navigate(JsonNode host, String id, String destination) {
        return post(base(host) + "/actions/navigate").header("X-Player-Token", token(host)).content(
                "{\"actionId\":\"" + id + "\",\"destinationArticle\":\"" + destination + "\"}");
    }
    @Test void movementSpamReturnsStructured429AndDuplicatesStillSucceed() throws Exception {
        var host = active(); String id = UUID.randomUUID().toString();
        request(navigate(host, id, "Algorithm"), 200);
        for (int i = 1; i < 10; i++) request(navigate(host, UUID.randomUUID().toString(), i % 2 == 1 ? "Computer Science" : "Algorithm"), 200);
        var limited = request(navigate(host, UUID.randomUUID().toString(), "Algorithm"), 429);
        assertThat(limited.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(limited.get("timestamp").asText()).isNotBlank();
        assertThat(request(navigate(host, id, "Algorithm"), 200).get("outcome").asText()).isEqualTo("DUPLICATE");
        clock.advance(1); request(navigate(host, UUID.randomUUID().toString(), "Algorithm"), 200);
    }

    @Test void articleEndpointIsPrivateAndServerSelected() throws Exception {
        var host = active();
        mvc.perform(get(base(host) + "/me/article")).andExpect(status().isUnauthorized());
        mvc.perform(get(base(host) + "/me/article").header("X-Player-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get(base(host) + "/me/article").header("X-Player-Token", token(host)).param("title", "Target"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base(host) + "/me/article").header("X-Player-Token", token(host)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("Computer Science"))
                .andExpect(jsonPath("$.html").isNotEmpty()).andExpect(jsonPath("$.outgoingLinks").doesNotExist());
        request(navigate(host, UUID.randomUUID().toString(), "Algorithm"), 200);
        mvc.perform(get(base(host) + "/me/article").header("X-Player-Token", token(host)))
                .andExpect(jsonPath("$.title").value("Algorithm")).andExpect(jsonPath("$.movementRevision").value(1));
    }

    @Test void searchValidatesQueryAndReturnsSmallTitleResults() throws Exception {
        mvc.perform(get("/api/wiki/search").param("q", "Graph")).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].title").value("Graph Theory"));
        mvc.perform(get("/api/wiki/search").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wiki/search").param("q", "a".repeat(301))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wiki/search")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void creationAndReadsHavePrivateCacheHeadersAndNoTokenLeak() throws Exception {
        var result = mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Host\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        var host = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(base(host));
        mvc.perform(get(base(host)).header("X-Player-Token", token(host)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.playerSessionToken").doesNotExist())
                .andExpect(jsonPath("$.room.players[0].tokenDigest").doesNotExist());
        assertThat(host.get("view").toString()).doesNotContain(token(host));
    }

    @Test void completeRestRaceSupportsBackDuplicateCloseRedactionAndWinner() throws Exception {
        var host = active();
        String id = UUID.randomUUID().toString();
        var moved = request(navigate(host, id, "Algorithm"), 200);
        assertThat(moved.at("/view/me/clickCount").asInt()).isEqualTo(1);
        var duplicate = request(navigate(host, id, "Algorithm"), 200);
        assertThat(duplicate.get("outcome").asText()).isEqualTo("DUPLICATE");
        String backId = UUID.randomUUID().toString();
        request(post(base(host) + "/actions/back").header("X-Player-Token", token(host))
                .content("{\"actionId\":\"" + backId + "\"}"), 200);
        var close = request(navigate(host, UUID.randomUUID().toString(), "Mathematics"), 200);
        assertThat(close.at("/view/me/currentArticle").asText()).isEqualTo("Mathematics");
        assertThat(close.at("/view/room/players/0/currentArticle").isNull()).isTrue();
        assertThat(close.at("/view/room/players/0/closeToTarget").asBoolean()).isTrue();
        String shared = close.at("/view/room").toString();
        assertThat(shared).doesNotContain("Mathematics", token(host), "visitLog", "history", "tokenDigest");
        assertThat(close.at("/view/me").has("closeToTarget")).isFalse();
        var won = request(navigate(host, UUID.randomUUID().toString(), "Quantum Mechanics"), 200);
        assertThat(won.at("/view/room/status").asText()).isEqualTo("FINISHED");
        assertThat(won.at("/view/room/winnerPlayerId").asText()).isEqualTo(host.get("playerId").asText());
        assertThat(won.at("/view/me/clickCount").asInt()).isEqualTo(4);
        assertThat(request(navigate(host, UUID.randomUUID().toString(), "Physics"), 409).get("code").asText()).isEqualTo("RACE_FINISHED");
    }

    @Test void authAndHostErrorsUseStructuredStatusCodes() throws Exception {
        var host = create();
        var guest = request(post(base(host) + "/join").content("{\"displayName\":\"Guest\"}"), 201);
        var unauthorized = request(get(base(host)), 401);
        assertThat(unauthorized.get("code").asText()).isEqualTo("INVALID_PLAYER_TOKEN");
        assertThat(unauthorized.has("timestamp")).isTrue();
        var denied = request(patch(base(host) + "/settings").header("X-Player-Token", token(guest)).content("{}"), 403);
        assertThat(denied.get("code").asText()).isEqualTo("NOT_HOST");
        assertThat(request(get("/api/rooms/AAAAAA"), 404).get("code").asText()).isEqualTo("ROOM_NOT_FOUND");
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"displayName\":\"Host\",\"winner\":true}", "{\"displayName\":3}",
            "{\"displayName\":3.5}", "{\"displayName\":true}", "null", "[]", "{"})
    void malformedOrForgedCreateRequestsAreRejected(String body) throws Exception {
        assertThat(request(post("/api/rooms").content(body), 400).get("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @ParameterizedTest @ValueSource(strings = {"A", "Alex🙂", "A@B"})
    void invalidDisplayNameUsesDocumentedError(String name) throws Exception {
        var body = mapper.createObjectNode().put("displayName", name).toString();
        assertThat(request(post("/api/rooms").content(body), 400).get("code").asText()).isEqualTo("INVALID_DISPLAY_NAME");
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"ready\":\"true\"}", "{\"ready\":null}", "{}", "{\"ready\":true,\"currentArticle\":\"Physics\"}"})
    void readyRequestsRejectWrongTypesOrAuthoritativeFields(String body) throws Exception {
        var host = create();
        assertThat(request(post(base(host) + "/ready").header("X-Player-Token", token(host)).content(body), 400)
                .get("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"timeLimitSeconds\":180.5}", "{\"timeLimitSeconds\":2147483648}",
            "{\"unlimited\":\"true\"}", "{\"unlimited\":null}", "{\"startArticle\":5}", "{\"winner\":true}", "[]"})
    void settingsRejectWrongTypesOrUndocumentedFields(String body) throws Exception {
        var host = create();
        assertThat(request(patch(base(host) + "/settings").header("X-Player-Token", token(host)).content(body), 400)
                .get("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @Test void backCannotSupplyDestinationAndMovesRequireValidUuid() throws Exception {
        var host = active();
        var forbidden = request(post(base(host) + "/actions/back").header("X-Player-Token", token(host))
                .content("{\"actionId\":\"" + UUID.randomUUID() + "\",\"destinationArticle\":\"Quantum Mechanics\"}"), 400);
        assertThat(forbidden.get("code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(request(navigate(host, "invalid-uuid", "Algorithm"), 400).get("code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(request(navigate(host, UUID.randomUUID().toString(), "Quantum Mechanics"), 422).get("code").asText())
                .isEqualTo("INVALID_NAVIGATION");
        var noBack = request(post(base(host) + "/actions/back").header("X-Player-Token", token(host))
                .content("{\"actionId\":\"" + UUID.randomUUID() + "\"}"), 409);
        assertThat(noBack.get("code").asText()).isEqualTo("NO_BACK_HISTORY");
    }

    @Test void gameplayCorsPermitsPhase2MethodsAndTokenHeader() throws Exception {
        mvc.perform(options("/api/rooms/AAAAAA/settings").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "X-Player-Token,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test void existingEndpointsRejectUnknownResultsAndUnstartedArticles() throws Exception {
        mvc.perform(get("/api/wiki/search?q=Algorithm")).andExpect(status().isOk());
        mvc.perform(get("/api/races/" + UUID.randomUUID() + "/results")).andExpect(status().isNotFound());
        var host = create();
        mvc.perform(get(base(host) + "/me/article").header("X-Player-Token", token(host))).andExpect(status().isConflict());
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"displayName\":\"Host\"} {\"displayName\":\"Other\"}",
            "{\"displayName\":\"Host\",\"displayName\":\"Other\"}"})
    void ambiguousCreateJsonIsRejected(String body) throws Exception {
        assertThat(request(post("/api/rooms").content(body), 400).get("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"unlimited\":true} {}", "{\"unlimited\":true,\"unlimited\":false}"})
    void ambiguousSettingsJsonIsRejectedWithoutChangingState(String body) throws Exception {
        var host = create();
        var before = request(get(base(host)).header("X-Player-Token", token(host)), 200);
        request(patch(base(host) + "/settings").header("X-Player-Token", token(host)).content(body), 400);
        assertThat(request(get(base(host)).header("X-Player-Token", token(host)), 200).get("room"))
                .isEqualTo(before.get("room"));
    }

    @ParameterizedTest @ValueSource(strings = {"currentArticle", "clickCount", "winnerPlayerId", "visitLog", "history", "closeToTarget"})
    void clientCannotInjectAuthoritativeMovementState(String field) throws Exception {
        var host = active();
        var body = mapper.createObjectNode().put("actionId", UUID.randomUUID().toString())
                .put("destinationArticle", "Algorithm").put(field, "forged");
        var error = request(post(base(host) + "/actions/navigate").header("X-Player-Token", token(host)).content(body.toString()), 400);
        assertThat(error.get("code").asText()).isEqualTo("INVALID_REQUEST");
        var after = request(get(base(host)).header("X-Player-Token", token(host)), 200);
        assertThat(after.at("/me/clickCount").asInt()).isZero();
        assertThat(after.at("/me/currentArticle").asText()).isEqualTo("Computer Science");
        assertThat(after.at("/room/winnerPlayerId").isNull()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void credentialsAreAbsentFromErrorsAndApplicationLogs(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var host = create();
        String secret = "phase9-secret-" + UUID.randomUUID();
        var denied = request(get(base(host)).header("X-Player-Token", secret), 401);
        var malformed = request(post(base(host) + "/actions/navigate").header("X-Player-Token", token(host))
                .content("{\"actionId\":\"" + secret + "\"}"), 400);
        assertThat(denied.toString() + malformed).doesNotContain(secret, token(host));
        assertThat(output.getAll()).doesNotContain(secret, token(host));
    }

    @Test void hostileDisplayNamesAndForeignOriginCannotCreateRooms() throws Exception {
        request(post("/api/rooms").content(mapper.createObjectNode().put("displayName", "<img onerror=x>").toString()), 400);
        mvc.perform(post("/api/rooms").header("Origin", "https://evil.example")
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Host\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
