package com.wikirace.game.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.wikirace.dto.GameDtos.SessionResponse;
import com.wikirace.exception.GameException;
import com.wikirace.game.model.*;
import com.wikirace.game.runtime.*;
import com.wikirace.wikipedia.FakeArticleProvider;
import com.wikirace.websocket.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.wikirace.exception.GameException.Code.*;

class NetworkHardeningTest {
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        void seconds(long seconds) { now = now.plusSeconds(seconds); }
    }
    final TestClock clock = new TestClock();
    final RoomRegistry registry = new RoomRegistry();
    final List<Object> events = new CopyOnWriteArrayList<>();
    final GameService games = new GameService(registry, new FakeArticleProvider(), clock, 2048, events::add,
            new GameLimits(10, Duration.ofMinutes(5), Duration.ofMinutes(30)));
    final SessionResponse host = games.create("Host");
    final String code = host.view().room().roomCode();
    final SessionResponse guest = games.join(code, "Guest");
    void online(SessionResponse player, String socket) { games.socketPresence(code, player.playerId(), socket, true); }
    void start(boolean timed) {
        games.configure(code, host.playerSessionToken(), new SettingsPatch(true, "Computer Science", true, "Quantum Mechanics", !timed, true, timed ? 180 : null));
        games.ready(code, host.playerSessionToken(), true); games.ready(code, guest.playerSessionToken(), true);
        games.start(code, host.playerSessionToken());
    }
    com.wikirace.dto.GameDtos.ActionResponse move(SessionResponse player, UUID id, String title) {
        return games.move(code, player.playerSessionToken(), new MoveAction(id, MoveType.NAVIGATE, title));
    }
    void error(GameException.Code code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(GameException.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }
    @Test void tenRequestsAreAllowedAndWindowRecoversWithoutChangingRejectedState() {
        start(false); clock.seconds(3);
        for (int i = 0; i < 10; i++) move(host, UUID.randomUUID(), i % 2 == 0 ? "Algorithm" : "Computer Science");
        long version = games.get(code, host.playerSessionToken()).room().version();
        error(RATE_LIMITED, () -> move(host, UUID.randomUUID(), "Algorithm"));
        assertThat(games.get(code, host.playerSessionToken()).room().version()).isEqualTo(version);
        assertThat(games.get(code, host.playerSessionToken()).me().clickCount()).isEqualTo(10);
        move(guest, UUID.randomUUID(), "Algorithm");
        clock.seconds(1); move(host, UUID.randomUUID(), "Algorithm");
        assertThat(games.get(code, host.playerSessionToken()).me().clickCount()).isEqualTo(11);
    }
    @Test void retainedDuplicatesBypassBudgetAndDifferentIntentStillConflicts() {
        start(false); clock.seconds(3); UUID id = UUID.randomUUID(); move(host, id, "Algorithm");
        for (int i = 1; i < 10; i++) move(host, UUID.randomUUID(), i % 2 == 1 ? "Computer Science" : "Algorithm");
        for (int i = 0; i < 30; i++) assertThat(move(host, id, "Algorithm").outcome()).isEqualTo("DUPLICATE");
        error(ACTION_ID_CONFLICT, () -> move(host, id, "Mathematics"));
        error(RATE_LIMITED, () -> move(host, UUID.randomUUID(), "Algorithm"));
        assertThat(games.get(code, host.playerSessionToken()).me().clickCount()).isEqualTo(10);
    }
    @Test void rejectedMovesConsumeBudgetButDoNotEnterAcceptedRecords() {
        start(false); clock.seconds(3); UUID id = UUID.randomUUID();
        for (int i = 0; i < 10; i++) error(INVALID_NAVIGATION, () -> move(host, id, "Quantum Mechanics"));
        error(RATE_LIMITED, () -> move(host, id, "Quantum Mechanics"));
        clock.seconds(1); move(host, id, "Algorithm");
        assertThat(games.get(code, host.playerSessionToken()).me().clickCount()).isEqualTo(1);
    }
    @Test void concurrentSpamCannotExceedOnePlayersBudget() throws Exception {
        start(false); clock.seconds(3);
        var gate = new CountDownLatch(1); var results = new CopyOnWriteArrayList<GameException.Code>();
        try (var executor = Executors.newFixedThreadPool(20)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 20; i++) futures.add(executor.submit(() -> {
                try { gate.await(); move(host, UUID.randomUUID(), "Quantum Mechanics"); }
                catch (GameException failure) { results.add(failure.code()); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }));
            gate.countDown(); for (var future : futures) future.get(3, TimeUnit.SECONDS);
        }
        assertThat(results.stream().filter(c -> c == RATE_LIMITED)).hasSize(10);
        assertThat(results.stream().filter(c -> c == INVALID_NAVIGATION)).hasSize(10);
    }
    @Test void newlyJoinedGuestGetsFullGraceThenSlotAndTokenAreRemoved() {
        online(host, "host"); clock.seconds(299); games.cleanupRooms();
        assertThat(games.get(code, host.playerSessionToken()).room().players()).hasSize(2);
        clock.seconds(1); games.cleanupRooms();
        assertThat(games.get(code, host.playerSessionToken()).room().players()).hasSize(1);
        error(INVALID_PLAYER_TOKEN, () -> games.get(code, guest.playerSessionToken()));
        assertThat(((RoomEvent) events.getLast()).type()).isEqualTo(RoomEvent.Type.ROOM_UPDATED);
        assertThat(games.join(code, "Replacement").view().room().players()).hasSize(2);
    }
    @Test void reconnectResetsGraceAndOtherSocketsKeepPlayerLive() {
        online(host, "host"); clock.seconds(299); online(guest, "one"); online(guest, "two");
        games.socketPresence(code, guest.playerId(), "one", false); clock.seconds(600); games.cleanupRooms();
        assertThat(games.get(code, guest.playerSessionToken()).room().players()).hasSize(2);
        games.socketPresence(code, guest.playerId(), "two", false); clock.seconds(299); games.cleanupRooms();
        assertThat(games.get(code, guest.playerSessionToken()).room().players()).hasSize(2);
        clock.seconds(1); games.cleanupRooms(); error(INVALID_PLAYER_TOKEN, () -> games.get(code, guest.playerSessionToken()));
    }
    @Test void abandonedHostExpiresWholeLobbyWithoutMigrationOrNewIdentity() {
        online(guest, "guest"); clock.seconds(300); games.cleanupRooms();
        error(ROOM_NOT_FOUND, () -> games.get(code, host.playerSessionToken()));
        assertThat(events.getLast()).isInstanceOf(RoomExpired.class);
        assertThat(registry.all()).isEmpty();
    }
    @Test void countdownActiveAndSuddenDeathNeverExpirePlayers() {
        start(true); games.cleanupRooms(); assertThat(registry.find(code).status()).isEqualTo(RaceStatus.COUNTDOWN);
        clock.seconds(3); games.advanceRaces(); games.cleanupRooms(); assertThat(registry.find(code).players()).hasSize(2);
        clock.seconds(10000); games.advanceRaces(); games.cleanupRooms();
        assertThat(games.get(code, guest.playerSessionToken()).room().status()).isEqualTo(RaceStatus.SUDDEN_DEATH);
        assertThat(registry.find(code).players()).hasSize(2);
        move(host, UUID.randomUUID(), "Mathematics"); move(host, UUID.randomUUID(), "Quantum Mechanics");
    }
    @Test void finishedRetentionDoesNotExtendWithReadsOrReconnects() {
        start(false); clock.seconds(3); move(host, UUID.randomUUID(), "Mathematics");
        UUID winning = UUID.randomUUID(); move(host, winning, "Quantum Mechanics");
        clock.seconds(1799); online(host, "back"); games.cleanupRooms();
        assertThat(move(host, winning, "Quantum Mechanics").outcome()).isEqualTo("DUPLICATE");
        clock.seconds(1); games.cleanupRooms(); error(ROOM_NOT_FOUND, () -> games.get(code, host.playerSessionToken()));
        assertThat(registry.all()).isEmpty();
    }
    @Test void nonPositiveLimitsAreRejected() {
        assertThatThrownBy(() -> new GameLimits(0, Duration.ofMinutes(5), Duration.ofMinutes(30))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameLimits(10, Duration.ZERO, Duration.ofMinutes(30))).isInstanceOf(IllegalArgumentException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(RaceStatus.class)
    void reconnectRestoresPrivateStateAndAuthoritativeTimesInEveryStatus(RaceStatus status) {
        online(host, "before");
        if (status != RaceStatus.WAITING) start(status == RaceStatus.SUDDEN_DEATH);
        if (status == RaceStatus.ACTIVE || status == RaceStatus.SUDDEN_DEATH || status == RaceStatus.FINISHED) {
            clock.seconds(status == RaceStatus.SUDDEN_DEATH ? 183 : 3); games.advanceRaces();
            if (status == RaceStatus.FINISHED) {
                move(host, UUID.randomUUID(), "Mathematics"); move(host, UUID.randomUUID(), "Quantum Mechanics");
            } else move(host, UUID.randomUUID(), "Algorithm");
        }
        var before = games.get(code, host.playerSessionToken());
        games.socketPresence(code, host.playerId(), "before", false);
        online(host, "after");
        var restored = games.get(code, host.playerSessionToken());
        assertThat(restored.me()).isEqualTo(before.me());
        assertThat(restored.room().status()).isEqualTo(status);
        assertThat(restored.room().startsAt()).isEqualTo(before.room().startsAt());
        assertThat(restored.room().suddenDeathStartedAt()).isEqualTo(before.room().suddenDeathStartedAt());
        assertThat(restored.room().finishedAt()).isEqualTo(before.room().finishedAt());
        assertThat(restored.room().winnerPlayerId()).isEqualTo(before.room().winnerPlayerId());
        assertThat(restored.room().version()).isEqualTo(before.room().version() + 2);
        assertThat(restored.room().players()).hasSize(2);
    }
}
