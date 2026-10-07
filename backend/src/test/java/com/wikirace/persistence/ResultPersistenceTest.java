package com.wikirace.persistence;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikirace.WikiRaceApplication;
import com.wikirace.dto.RaceResults;
import com.wikirace.game.model.*;
import com.wikirace.game.runtime.RoomRegistry;
import com.wikirace.game.service.GameService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.game.tick-millis=600000", "app.game.cleanup-millis=600000"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence-test"})
@Import(ResultPersistenceTest.Config.class)
@Testcontainers
class ResultPersistenceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }
    public static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T00:00:00Z"));
        void seconds(long seconds) { now.updateAndGet(time -> time.plusSeconds(seconds)); }
        public Instant instant() { return now.get(); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    @TestConfiguration public static class Config { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    @Autowired GameService games;
    @Autowired RoomRegistry rooms;
    @Autowired ResultStore store;
    @Autowired TestClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    com.wikirace.dto.GameDtos.SessionResponse active(boolean timed) {
        var host = games.create("Host"); String code = host.view().room().roomCode(); var guest = games.join(code, "Guest");
        games.configure(code, host.playerSessionToken(), new SettingsPatch(true, "Computer Science", true, "Quantum Mechanics", !timed, true, timed ? 180 : null));
        games.ready(code, host.playerSessionToken(), true); games.ready(code, guest.playerSessionToken(), true);
        games.start(code, host.playerSessionToken()); clock.seconds(3); games.advanceRaces(); return host;
    }
    void navigate(com.wikirace.dto.GameDtos.SessionResponse host, String title) {
        games.move(host.view().room().roomCode(), host.playerSessionToken(), new MoveAction(UUID.randomUUID(), MoveType.NAVIGATE, title));
    }
    RaceResults finish(com.wikirace.dto.GameDtos.SessionResponse host) {
        navigate(host, "Algorithm");
        games.move(host.view().room().roomCode(), host.playerSessionToken(), new MoveAction(UUID.randomUUID(), MoveType.BACK, null));
        navigate(host, "Mathematics"); clock.seconds(2); navigate(host, "Quantum Mechanics");
        return games.completedResult(rooms.find(host.view().room().roomCode()).id()).orElseThrow();
    }
    long count(String table, UUID id) { return jdbc.queryForObject("select count(*) from " + table + " where " + (table.equals("completed_race") ? "id" : "race_id") + " = ?", Long.class, id); }

    @Test void completedAggregatePersistsOrderedBackVisitsSettingsAndBothPlayers() throws Exception {
        var host = active(false); var result = finish(host);
        assertThat(store.read(result.id())).contains(result);
        assertThat(result.players().getFirst().visitLog()).containsExactly("Computer Science", "Algorithm", "Computer Science", "Mathematics", "Quantum Mechanics");
        assertThat(result.players().getFirst().clickCount()).isEqualTo(4);
        assertThat(result.players().getLast().visitLog()).containsExactly("Computer Science");
        assertThat(result.players().stream().filter(RaceResults.PlayerResult::winner)).hasSize(1);
        assertThat(count("completed_race", result.id())).isEqualTo(1);
        assertThat(count("player_result", result.id())).isEqualTo(2);
        assertThat(count("visit_step", result.id())).isEqualTo(6);
        assertThat(games.get(result.roomCode(), host.playerSessionToken()).room().resultPersistenceStatus()).isEqualTo("SAVED");
        String response = mvc.perform(get("/api/races/" + result.id() + "/results")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(host.playerSessionToken(), "token", "history", "lock", "sessions", "connected");
        assertThat(mapper.readValue(response, RaceResults.class)).isEqualTo(result);
    }
    @Test void activeRoomsStayInMemoryAndCannotExposePartialResults() throws Exception {
        var host = active(false); var room = rooms.find(host.view().room().roomCode()); navigate(host, "Algorithm");
        assertThat(count("completed_race", room.id())).isZero();
        mvc.perform(get("/api/races/" + room.id() + "/results")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RACE_NOT_FINISHED"));
        mvc.perform(get("/api/races/not-a-uuid/results")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/races/" + UUID.randomUUID() + "/results")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RACE_NOT_FOUND"));
    }
    @Test void timedSuddenDeathMetadataAndElapsedTimeSurviveStorage() {
        var host = active(true); clock.seconds(188); games.advanceRaces(); var result = finish(host);
        assertThat(result.unlimited()).isFalse(); assertThat(result.timeLimitSeconds()).isEqualTo(180);
        assertThat(result.suddenDeathStartedAt()).isEqualTo(result.startedAt().plusSeconds(180));
        assertThat(result.elapsedSeconds()).isEqualTo(190);
        assertThat(store.read(result.id())).contains(result);
    }
    @Test void nanosecondRuntimeClockProducesExactMicrosecondResultRoundTrip() {
        clock.now.updateAndGet(now -> now.plusNanos(123456789));
        var result = finish(active(false));
        assertThat(result.startedAt().getNano() % 1000).isZero();
        assertThat(result.finishedAt().getNano() % 1000).isZero();
        assertThat(store.read(result.id())).contains(result);
    }
    @Test void duplicateAndConcurrentSavesCannotDuplicateOrOverwriteAggregate() throws Exception {
        var original = finish(active(false));
        var result = new RaceResults(UUID.randomUUID(), original.roomCode(), original.startArticle(), original.targetArticle(),
                original.unlimited(), original.timeLimitSeconds(), original.startedAt(), original.suddenDeathStartedAt(),
                original.finishedAt(), original.elapsedSeconds(), original.winnerPlayerId(), original.players());
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) jobs.add(executor.submit(() -> store.save(result)));
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        assertThat(store.read(result.id())).contains(result);
        assertThat(count("player_result", result.id())).isEqualTo(2); assertThat(count("visit_step", result.id())).isEqualTo(6);
    }
    @Test void simultaneousTargetArrivalsPersistExactlyOneWinner() throws Exception {
        var host = games.create("Host"); String code = host.view().room().roomCode(); var guest = games.join(code, "Guest");
        games.configure(code, host.playerSessionToken(), new SettingsPatch(true, "Computer Science", true, "Quantum Mechanics", true, true, null));
        games.ready(code, host.playerSessionToken(), true); games.ready(code, guest.playerSessionToken(), true);
        games.start(code, host.playerSessionToken()); clock.seconds(3); games.advanceRaces();
        navigate(host, "Mathematics"); navigate(guest, "Mathematics");
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (var player : List.of(host, guest)) jobs.add(executor.submit(() -> {
                try { gate.await(); navigate(player, "Quantum Mechanics"); }
                catch (com.wikirace.exception.GameException failure) {
                    assertThat(failure.code()).isEqualTo(com.wikirace.exception.GameException.Code.RACE_FINISHED);
                } catch (InterruptedException failure) { throw new AssertionError(failure); }
            }));
            gate.countDown(); for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        var result = store.read(rooms.find(code).id()).orElseThrow();
        assertThat(result.players().stream().filter(RaceResults.PlayerResult::winner)).hasSize(1);
        assertThat(result.winnerPlayerId()).isIn(host.playerId(), guest.playerId());
        assertThat(count("completed_race", result.id())).isEqualTo(1);
        assertThat(count("visit_step", result.id())).isEqualTo(5);
    }
    @Test void invalidChildRollsBackWholeAggregate() {
        var valid = finish(active(false)); UUID id = UUID.randomUUID();
        var first = valid.players().getFirst();
        var invalid = new RaceResults(id, valid.roomCode(), valid.startArticle(), valid.targetArticle(), true, null,
                valid.startedAt(), null, valid.finishedAt(), valid.elapsedSeconds(), valid.winnerPlayerId(),
                List.of(first, new RaceResults.PlayerResult(UUID.randomUUID(), "Guest", -1, "Computer Science", false, List.of("Computer Science"))));
        assertThatThrownBy(() -> store.save(invalid)).isInstanceOf(RuntimeException.class);
        assertThat(store.read(id)).isEmpty(); assertThat(count("player_result", id)).isZero(); assertThat(count("visit_step", id)).isZero();
    }
    @Test void databaseWriteFailureLeavesWinnerAndRetainedResultsIntact() throws Exception {
        var host = active(false); String code = host.view().room().roomCode();
        jdbc.execute("ALTER TABLE completed_race ADD CONSTRAINT test_reject_race CHECK (room_code <> '" + code + "')");
        RaceResults result;
        try { result = finish(host); }
        finally { jdbc.execute("ALTER TABLE completed_race DROP CONSTRAINT test_reject_race"); }
        var view = games.get(code, host.playerSessionToken());
        assertThat(view.room().status()).isEqualTo(RaceStatus.FINISHED); assertThat(view.room().winnerPlayerId()).isEqualTo(host.playerId());
        assertThat(view.room().resultPersistenceStatus()).isEqualTo("FAILED"); assertThat(store.read(result.id())).isEmpty();
        mvc.perform(get("/api/races/" + result.id() + "/results")).andExpect(status().isOk()).andExpect(jsonPath("$.winnerPlayerId").value(host.playerId().toString()));
    }
    @Test void cleanupRemovesRuntimeRoomButSavedResultsRemainPublic() throws Exception {
        var result = finish(active(false)); clock.seconds(1800); games.cleanupRooms();
        assertThat(rooms.all().stream().anyMatch(room -> room.id().equals(result.id()))).isFalse();
        mvc.perform(get("/api/races/" + result.id() + "/results")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(result.id().toString()));
    }
    @Test void schemaContainsNoSessionOrRuntimeColumnsAndFlywayOwnsSchema() {
        var columns = jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name in ('completed_race','player_result','visit_step')", String.class);
        assertThat(columns).noneMatch(name -> name.contains("token") || name.contains("socket") || name.contains("lock") || name.contains("session") || name.contains("history"));
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(1);
    }
    org.springframework.context.ConfigurableApplicationContext application() {
        return new SpringApplicationBuilder(WikiRaceApplication.class, Config.class).profiles("test", "persistence-test")
                .run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(), "--app.game.tick-millis=600000", "--app.game.cleanup-millis=600000");
    }
    @Test void savedResultsSurviveActualApplicationShutdownAndRestart() {
        var result = finish(active(false));
        try (var first = application()) {
            assertThat(first.getBean(ResultStore.class).read(result.id())).contains(result);
            first.getBean(GameService.class).create("Temporary");
            assertThat(first.getBean(RoomRegistry.class).all()).hasSize(1);
        }
        try (var restarted = application()) {
            assertThat(restarted.getBean(RoomRegistry.class).all()).isEmpty();
            assertThat(restarted.getBean(ResultService.class).get(result.id())).isEqualTo(result);
        }
    }
}
