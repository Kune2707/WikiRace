package com.wikirace.websocket;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikirace.game.service.GameService;
import com.wikirace.game.runtime.RoomRegistry;
import com.wikirace.game.model.*;
import com.wikirace.wikipedia.FakeArticleProvider;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class RoomEventsTest {
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    final TestClock clock = new TestClock();
    final RoomRegistry rooms = new RoomRegistry();
    final List<RoomEvent> events = new CopyOnWriteArrayList<>();
    final GameService games = new GameService(rooms, new FakeArticleProvider(), clock, 2048, event -> {
        if (!(event instanceof RoomEvent)) return;
        RoomEvent roomEvent = (RoomEvent) event;
        assertThat(rooms.find(roomEvent.roomCode()).lock().isHeldByCurrentThread()).isFalse();
        events.add(roomEvent);
    });
    final com.wikirace.dto.GameDtos.SessionResponse host = games.create("Host");
    final String code = host.view().room().roomCode();
    final String token = host.playerSessionToken();
    final com.wikirace.dto.GameDtos.SessionResponse guest = games.join(code,"Guest");

    void active() {
        games.configure(code,token,new SettingsPatch(true,"Computer Science",true,"Quantum Mechanics",true,true,null));
        games.ready(code,token,true); games.ready(code,guest.playerSessionToken(),true);
        games.start(code,token); clock.now = clock.now.plusSeconds(3); games.advanceRaces();
    }
    void move(String token, String article) { games.move(code,token,new MoveAction(UUID.randomUUID(),MoveType.NAVIGATE,article)); }

    @Test void lifecycleSnapshotsHaveUniqueIncreasingVersionsAndNoopDoesNotBroadcast() {
        active();
        assertThat(events.stream().map(RoomEvent::type)).containsExactly(RoomEvent.Type.PLAYER_JOINED,RoomEvent.Type.SETTINGS_UPDATED,
                RoomEvent.Type.PLAYER_READY_CHANGED,RoomEvent.Type.PLAYER_READY_CHANGED,RoomEvent.Type.COUNTDOWN_STARTED,RoomEvent.Type.RACE_STARTED);
        assertThat(events.stream().map(RoomEvent::version)).isSorted().doesNotHaveDuplicates();
        int count = events.size(); games.get(code,token); assertThat(events).hasSize(count);
    }
    @Test void hiddenArticleAndSecretsAreAbsentInSerializedPayloadAndDuplicateDoesNotBroadcast() throws Exception {
        active(); UUID id = UUID.randomUUID();
        games.move(code,token,new MoveAction(id,MoveType.NAVIGATE,"Mathematics"));
        var event = events.getLast();
        assertThat(event.type()).isEqualTo(RoomEvent.Type.PLAYER_CLOSE_CHANGED);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        assertThat(json).doesNotContain("Mathematics",token,guest.playerSessionToken(),"tokenDigest","visitLog","movementRevision","\"html\"","\"me\"");
        int count = events.size(); games.move(code,token,new MoveAction(id,MoveType.NAVIGATE,"Mathematics")); assertThat(events).hasSize(count);
        assertThat(event.room().players().getFirst().currentArticle()).isNull();
    }
    @Test void overdueTransitionsAreSeparatelyFrozenBeforeSuddenDeath() {
        games.configure(code,token,new SettingsPatch(true,"Computer Science",true,"Quantum Mechanics",false,true,180));
        games.ready(code,token,true); games.ready(code,guest.playerSessionToken(),true); games.start(code,token);
        clock.now = clock.now.plusSeconds(190); games.advanceRaces();
        var last = events.subList(events.size()-2,events.size());
        assertThat(last.getFirst().type()).isEqualTo(RoomEvent.Type.RACE_STARTED);
        assertThat(last.getFirst().room().status()).isEqualTo(RaceStatus.ACTIVE);
        assertThat(last.getLast().type()).isEqualTo(RoomEvent.Type.SUDDEN_DEATH_STARTED);
        assertThat(last.getLast().version()).isEqualTo(last.getFirst().version()+1);
    }
    @Test void presenceCountsSessionsAndDisconnectNeverCancelsRace() {
        active(); games.socketPresence(code,host.playerId(),"first",true);
        long version = games.get(code,token).room().version();
        games.socketPresence(code,host.playerId(),"second",true); games.socketPresence(code,host.playerId(),"first",false);
        assertThat(games.get(code,token).room().version()).isEqualTo(version);
        games.socketPresence(code,host.playerId(),"second",false); games.socketPresence(code,host.playerId(),"second",false);
        assertThat(games.get(code,token).room().version()).isEqualTo(version+1);
        assertThat(events.getLast().type()).isEqualTo(RoomEvent.Type.PLAYER_DISCONNECTED);
        assertThat(games.get(code,token).room().status()).isEqualTo(RaceStatus.ACTIVE);
        games.socketPresence(code,host.playerId(),"third",true);
        assertThat(events.getLast().type()).isEqualTo(RoomEvent.Type.PLAYER_RECONNECTED);
    }
    @Test void simultaneousTargetArrivalsEmitExactlyOneFinishedSnapshot() throws Exception {
        active(); move(token,"Mathematics"); move(guest.playerSessionToken(),"Mathematics");
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (String playerToken : List.of(token,guest.playerSessionToken())) futures.add(executor.submit(() -> {
                try { gate.await(); move(playerToken,"Quantum Mechanics"); } catch (com.wikirace.exception.GameException expected) {
                    assertThat(expected.code()).isEqualTo(com.wikirace.exception.GameException.Code.RACE_FINISHED);
                } catch (InterruptedException e) { throw new AssertionError(e); }
            }));
            gate.countDown(); for (var future : futures) future.get(5,TimeUnit.SECONDS);
        }
        assertThat(events.stream().filter(e -> e.type() == RoomEvent.Type.RACE_FINISHED)).hasSize(1);
        assertThat(games.get(code,token).room().winnerPlayerId()).isIn(host.playerId(),guest.playerId());
    }
    @Test void broadcastFailureCannotRollBackACommittedWinner() {
        var service = new GameService(rooms,new FakeArticleProvider(),clock,2048,event -> { throw new IllegalStateException("Unavailable broker"); });
        active(); move(token,"Mathematics");
        service.move(code,token,new MoveAction(UUID.randomUUID(),MoveType.NAVIGATE,"Quantum Mechanics"));
        assertThat(games.get(code,token).room().winnerPlayerId()).isEqualTo(host.playerId());
    }
}
