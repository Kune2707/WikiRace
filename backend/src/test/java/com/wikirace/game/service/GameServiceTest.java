package com.wikirace.game.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.wikirace.dto.GameDtos.*;
import com.wikirace.exception.GameException;
import com.wikirace.game.model.*;
import com.wikirace.game.runtime.RoomRegistry;
import com.wikirace.wikipedia.*;
import static com.wikirace.exception.GameException.Code.*;
import static org.assertj.core.api.Assertions.*;

@Timeout(10)
class GameServiceTest {
    private MutableClock clock;
    private RoomRegistry registry;
    private GameService game;
    private SessionResponse host;
    private SessionResponse guest;
    private String code;

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T00:00:00Z"));
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @BeforeEach
    void setup() {
        clock = new MutableClock();
        registry = new RoomRegistry();
        game = new GameService(registry, new FakeArticleProvider(), clock, 2048);
        host = game.create("Host");
        code = host.view().room().roomCode();
        guest = game.join(code, "Guest");
    }

    private RoomView get() { return game.get(code, host.playerSessionToken()); }
    private SettingsPatch settings(String start, String target, boolean unlimited, Integer seconds) {
        return new SettingsPatch(true, start, true, target, unlimited, true, seconds);
    }
    private void configure(boolean unlimited, Integer seconds) {
        game.configure(code, host.playerSessionToken(), settings("Computer Science", "Quantum Mechanics", unlimited, seconds));
    }
    private void readyBoth() {
        game.ready(code, host.playerSessionToken(), true);
        game.ready(code, guest.playerSessionToken(), true);
    }
    private void active(boolean unlimited, Integer seconds) {
        configure(unlimited, seconds);
        readyBoth();
        game.start(code, host.playerSessionToken());
        clock.advance(3);
        game.advanceRaces();
    }
    private ActionResponse navigate(String title) { return navigate(host, UUID.randomUUID(), title); }
    private ActionResponse navigate(SessionResponse player, UUID id, String title) {
        return game.move(code, player.playerSessionToken(), new MoveAction(id, MoveType.NAVIGATE, title));
    }
    private ActionResponse back(UUID id) {
        return game.move(code, host.playerSessionToken(), new MoveAction(id, MoveType.BACK, null));
    }
    private PlayerRaceState internalPlayer() {
        return registry.find(code).players().stream().filter(player -> player.id().equals(host.playerId())).findFirst().orElseThrow();
    }
    private void expectCode(GameException.Code expected, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(GameException.class,
                exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    @Test void createsUuidRoomAndHostWithOpaqueToken() {
        assertThat(registry.find(code).id()).isNotNull();
        assertThat(host.playerId()).isEqualTo(host.view().room().hostPlayerId());
        assertThat(host.playerSessionToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(host.view().room().status()).isEqualTo(RaceStatus.WAITING);
        assertThat(host.view().room().version()).isEqualTo(1);
        assertThat(host.view().me().clickCount()).isZero();
        assertThat(host.view().me().currentArticle()).isNull();
    }

    @Test void codesAreReadableAndUnique() {
        var codes = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String generated = game.create("Player").view().room().roomCode();
            assertThat(generated).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
            assertThat(codes.add(generated)).isTrue();
        }
    }

    @Test void joiningCreatesDistinctIdentityAndRejectsFifthPlayer() {
        assertThat(guest.playerId()).isNotEqualTo(host.playerId());
        assertThat(guest.playerSessionToken()).isNotEqualTo(host.playerSessionToken());
        game.join(code, "Third");
        game.join(code, "Fourth");
        expectCode(ROOM_FULL, () -> game.join(code, "Fifth"));
        assertThat(get().room().players()).hasSize(4);
    }

    @Test void missingRoomIsRejected() {
        expectCode(ROOM_NOT_FOUND, () -> game.join("AAAAAA", "Guest"));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"A", "123456789012345678901", "Alex🙂", "A@B", "A\nB"})
    void invalidNamesAreRejected(String name) {
        expectCode(INVALID_DISPLAY_NAME, () -> game.create(name));
        expectCode(INVALID_DISPLAY_NAME, () -> game.join(code, name));
    }

    @ParameterizedTest @ValueSource(strings = {"Élodie", "王小明", "O'Neil_2", "A-B. C", "𐐀𐐁"})
    void unicodeAndSupportedPunctuationAreAccepted(String name) {
        assertThat(game.create(name).view().room().players().getFirst().displayName()).isEqualTo(name);
    }

    @Test void tokenIsRequiredAndScopedToRoom() {
        expectCode(INVALID_PLAYER_TOKEN, () -> game.get(code, null));
        expectCode(INVALID_PLAYER_TOKEN, () -> game.get(code, "fake"));
        var other = game.create("Other");
        expectCode(INVALID_PLAYER_TOKEN, () -> game.get(code, other.playerSessionToken()));
    }

    @Test void hostSettingsAreCanonicalizedAndResetAllReadiness() {
        readyBoth();
        var view = game.configure(code, host.playerSessionToken(), settings("computer_science", "quantum mechanics", false, 180));
        assertThat(view.room().settings().startArticle()).isEqualTo("Computer Science");
        assertThat(view.room().settings().targetArticle()).isEqualTo("Quantum Mechanics");
        assertThat(view.room().players()).allMatch(player -> !player.ready());
    }

    @Test void nonHostCannotConfigureOrStart() {
        expectCode(NOT_HOST, () -> game.configure(code, guest.playerSessionToken(), settings("Algorithm", "Physics", true, null)));
        expectCode(NOT_HOST, () -> game.start(code, guest.playerSessionToken()));
    }

    @Test void identicalSettingsPreserveReadinessAndVersion() {
        configure(true, null);
        readyBoth();
        long version = get().room().version();
        configure(true, null);
        assertThat(get().room().version()).isEqualTo(version);
        assertThat(get().room().players()).allMatch(PublicPlayer::ready);
    }

    @Test void partialSettingsMergeAndExplicitNullClear() {
        configure(true, null);
        var changed = game.configure(code, host.playerSessionToken(), new SettingsPatch(true, "Algorithm", false, null, null, false, null));
        assertThat(changed.room().settings().startArticle()).isEqualTo("Algorithm");
        assertThat(changed.room().settings().targetArticle()).isEqualTo("Quantum Mechanics");
        game.configure(code, host.playerSessionToken(), new SettingsPatch(true, null, false, null, null, false, null));
        readyBoth();
        expectCode(INVALID_ROOM_SETTINGS, () -> game.start(code, host.playerSessionToken()));
    }

    @Test void rejectsEqualArticlesAndInvalidTimerWithoutMutation() {
        long version = get().room().version();
        expectCode(INVALID_ROOM_SETTINGS, () -> game.configure(code, host.playerSessionToken(), settings("algorithm", "Algorithm", true, null)));
        expectCode(INVALID_ROOM_SETTINGS, () -> configure(false, 179));
        expectCode(INVALID_ROOM_SETTINGS, () -> configure(false, null));
        expectCode(INVALID_ROOM_SETTINGS, () -> configure(true, 180));
        expectCode(ARTICLE_NOT_FOUND, () -> game.configure(code, host.playerSessionToken(), settings("Missing", "Algorithm", true, null)));
        assertThat(get().room().version()).isEqualTo(version);
    }

    @Test void readyCanToggleAndIdenticalRequestsAreNoOps() {
        long version = get().room().version();
        game.ready(code, host.playerSessionToken(), true);
        assertThat(get().room().players().getFirst().ready()).isTrue();
        assertThat(get().room().version()).isEqualTo(version + 1);
        game.ready(code, host.playerSessionToken(), true);
        assertThat(get().room().version()).isEqualTo(version + 1);
        game.ready(code, host.playerSessionToken(), false);
        assertThat(get().room().players().getFirst().ready()).isFalse();
    }

    @Test void cannotStartWithOnePlayerOrUnreadyPlayers() {
        var solo = game.create("Solo");
        String soloCode = solo.view().room().roomCode();
        game.configure(soloCode, solo.playerSessionToken(), settings("Algorithm", "Physics", true, null));
        game.ready(soloCode, solo.playerSessionToken(), true);
        expectCode(PLAYERS_NOT_READY, () -> game.start(soloCode, solo.playerSessionToken()));
        configure(true, null);
        game.ready(code, host.playerSessionToken(), true);
        expectCode(PLAYERS_NOT_READY, () -> game.start(code, host.playerSessionToken()));
        game.ready(code, host.playerSessionToken(), false);
        game.ready(code, guest.playerSessionToken(), true);
        expectCode(PLAYERS_NOT_READY, () -> game.start(code, host.playerSessionToken()));
    }

    @Test void countdownUsesServerTimestampAndHidesArticleUntilActive() {
        configure(true, null);
        readyBoth();
        var start = game.start(code, host.playerSessionToken());
        assertThat(start.room().status()).isEqualTo(RaceStatus.COUNTDOWN);
        assertThat(start.room().startsAt()).isEqualTo(clock.instant().plusSeconds(3));
        assertThat(start.me().currentArticle()).isNull();
        assertThat(start.room().players()).allMatch(player -> player.currentArticle() == null);
        expectCode(INVALID_RACE_STATE, () -> navigate("Algorithm"));
        expectCode(INVALID_RACE_STATE, () -> back(UUID.randomUUID()));
        clock.advance(2);
        assertThat(get().room().status()).isEqualTo(RaceStatus.COUNTDOWN);
        clock.advance(1);
        assertThat(get().room().status()).isEqualTo(RaceStatus.ACTIVE);
        assertThat(get().me().currentArticle()).isEqualTo("Computer Science");
        assertThat(internalPlayer().visitLog()).containsExactly("Computer Science");
    }

    @Test void lobbyCommandsAreRejectedAfterStart() {
        active(true, null);
        expectCode(INVALID_RACE_STATE, () -> game.join(code, "Late"));
        expectCode(INVALID_RACE_STATE, () -> game.ready(code, host.playerSessionToken(), false));
        expectCode(INVALID_RACE_STATE, () -> configure(true, null));
        expectCode(INVALID_RACE_STATE, () -> game.start(code, host.playerSessionToken()));
    }

    @Test void movementWhileWaitingIsRejected() {
        expectCode(INVALID_RACE_STATE, () -> navigate("Algorithm"));
        expectCode(INVALID_RACE_STATE, () -> back(UUID.randomUUID()));
        assertThat(get().me().clickCount()).isZero();
    }

    @Test void timedDeadlineEntersIndefiniteSuddenDeathAtExactDeadline() {
        active(false, 180);
        Instant startsAt = get().room().startsAt();
        clock.advance(179);
        assertThat(get().room().status()).isEqualTo(RaceStatus.ACTIVE);
        clock.advance(1);
        game.advanceRaces();
        assertThat(get().room().status()).isEqualTo(RaceStatus.SUDDEN_DEATH);
        assertThat(get().room().suddenDeathStartedAt()).isEqualTo(startsAt.plusSeconds(180));
        clock.advance(10000);
        navigate("Mathematics");
        navigate("Quantum Mechanics");
        assertThat(get().room().status()).isEqualTo(RaceStatus.FINISHED);
        assertThat(get().room().winnerPlayerId()).isEqualTo(host.playerId());
    }

    @Test void lateSchedulerAdvancesBothTransitionsAndKeepsDeadline() {
        configure(false, 180);
        readyBoth();
        var start = game.start(code, host.playerSessionToken());
        clock.advance(500);
        game.advanceRaces();
        assertThat(get().room().version()).isEqualTo(start.room().version() + 2);
        assertThat(get().room().suddenDeathStartedAt()).isEqualTo(start.room().startsAt().plusSeconds(180));
    }

    @Test void unlimitedRaceNeverExpires() {
        active(true, null);
        long version = get().room().version();
        clock.advance(100000);
        game.advanceRaces();
        assertThat(get().room().status()).isEqualTo(RaceStatus.ACTIVE);
        assertThat(get().room().suddenDeathStartedAt()).isNull();
        assertThat(get().room().version()).isEqualTo(version);
    }

    @Test void validNavigationUpdatesHistoryClicksVisitsAndVersion() {
        active(true, null);
        long version = get().room().version();
        var moved = navigate("algorithm");
        assertThat(moved.outcome()).isEqualTo("APPLIED");
        assertThat(moved.view().me().currentArticle()).isEqualTo("Algorithm");
        assertThat(moved.view().me().clickCount()).isEqualTo(1);
        assertThat(moved.view().room().version()).isEqualTo(version + 1);
        assertThat(internalPlayer().history().entries()).containsExactly("Computer Science", "Algorithm");
        assertThat(internalPlayer().visitLog()).containsExactly("Computer Science", "Algorithm");
    }

    @Test void invalidMovementDoesNotChangeClicksHistoryOrVersion() {
        active(true, null);
        long version = get().room().version();
        expectCode(INVALID_NAVIGATION, () -> navigate("Quantum Mechanics"));
        expectCode(ARTICLE_NOT_FOUND, () -> navigate("Missing"));
        expectCode(NO_BACK_HISTORY, () -> back(UUID.randomUUID()));
        assertThat(get().me().clickCount()).isZero();
        assertThat(get().room().version()).isEqualTo(version);
        assertThat(internalPlayer().visitLog()).containsExactly("Computer Science");
    }

    @Test void backAndNewBranchKeepSeparateHistoryAndActualVisitLog() {
        active(true, null);
        navigate("Algorithm");
        navigate("Graph Theory");
        back(UUID.randomUUID());
        assertThat(get().me().currentArticle()).isEqualTo("Algorithm");
        assertThat(get().me().clickCount()).isEqualTo(3);
        assertThat(internalPlayer().history().cursor()).isEqualTo(1);
        navigate("Mathematics");
        assertThat(internalPlayer().history().entries()).containsExactly("Computer Science", "Algorithm", "Mathematics");
        assertThat(internalPlayer().history().cursor()).isEqualTo(2);
        assertThat(internalPlayer().visitLog()).containsExactly("Computer Science", "Algorithm", "Graph Theory", "Algorithm", "Mathematics");
        assertThat(get().me().clickCount()).isEqualTo(4);
    }

    @Test void closeArticleIsRedactedAndMovingAwayOrBackRecomputesClose() {
        active(true, null);
        navigate("Mathematics");
        var publicPlayer = game.get(code, guest.playerSessionToken()).room().players().getFirst();
        assertThat(publicPlayer.closeToTarget()).isTrue();
        assertThat(publicPlayer.currentArticle()).isNull();
        assertThat(get().me().currentArticle()).isEqualTo("Mathematics");
        navigate("Algorithm");
        assertThat(get().room().players().getFirst().closeToTarget()).isFalse();
        assertThat(get().room().players().getFirst().currentArticle()).isEqualTo("Algorithm");
        back(UUID.randomUUID());
        assertThat(get().room().players().getFirst().closeToTarget()).isTrue();
        assertThat(get().room().players().getFirst().currentArticle()).isNull();
        back(UUID.randomUUID());
        assertThat(get().room().players().getFirst().closeToTarget()).isFalse();
    }

    @Test void retainedNavigateAndBackDuplicatesNeverMoveTwice() {
        active(true, null);
        UUID id = UUID.randomUUID();
        var original = navigate(host, id, "Algorithm");
        var repeated = navigate(host, id, "Algorithm");
        assertThat(repeated.outcome()).isEqualTo("DUPLICATE");
        assertThat(repeated.appliedVersion()).isEqualTo(original.appliedVersion());
        assertThat(get().me().clickCount()).isEqualTo(1);
        UUID backId = UUID.randomUUID();
        back(backId);
        assertThat(back(backId).outcome()).isEqualTo("DUPLICATE");
        assertThat(get().me().clickCount()).isEqualTo(2);
        assertThat(internalPlayer().visitLog()).hasSize(3);
    }

    @Test void acceptedIdWithDifferentDestinationOrMoveTypeConflicts() {
        active(true, null);
        UUID id = UUID.randomUUID();
        navigate(host, id, "Algorithm");
        expectCode(ACTION_ID_CONFLICT, () -> navigate(host, id, "Mathematics"));
        expectCode(ACTION_ID_CONFLICT, () -> back(id));
        assertThat(get().me().clickCount()).isEqualTo(1);
    }

    @Test void failedActionIsNotRememberedAsSuccessful() {
        active(true, null);
        UUID id = UUID.randomUUID();
        expectCode(INVALID_NAVIGATION, () -> navigate(host, id, "Graph Theory"));
        assertThat(internalPlayer().accepted(id)).isNull();
        navigate("Algorithm");
        assertThat(navigate(host, id, "Graph Theory").outcome()).isEqualTo("APPLIED");
    }

    @Test void winnerIsPermanentAndWinningRetryIsSafeAfterFinish() {
        active(true, null);
        navigate("Mathematics");
        UUID id = UUID.randomUUID();
        navigate(host, id, "Quantum Mechanics");
        var finished = get();
        assertThat(finished.room().status()).isEqualTo(RaceStatus.FINISHED);
        assertThat(finished.room().winnerPlayerId()).isEqualTo(host.playerId());
        assertThat(finished.room().resultId()).isEqualTo(registry.find(code).id());
        assertThat(navigate(host, id, "Quantum Mechanics").outcome()).isEqualTo("DUPLICATE");
        expectCode(RACE_FINISHED, () -> navigate("Physics"));
        expectCode(RACE_FINISHED, () -> back(UUID.randomUUID()));
        clock.advance(1000);
        game.advanceRaces();
        assertThat(get().room().finishedAt()).isEqualTo(finished.room().finishedAt());
        assertThat(get().room().version()).isEqualTo(finished.room().version());
    }

    @Test void capacityEvictionIsInsertionOrderedAndOldRequestBecomesNew() {
        game = new GameService(registry, new FakeArticleProvider(), clock, 2);
        host = game.create("Host");
        code = host.view().room().roomCode();
        guest = game.join(code, "Guest");
        active(true, null);
        UUID first = UUID.randomUUID();
        navigate(host, first, "Algorithm");
        back(UUID.randomUUID());
        clock.advance(1000000);
        assertThat(navigate(host, first, "Algorithm").outcome()).isEqualTo("DUPLICATE");
        assertThat(get().me().currentArticle()).isEqualTo("Computer Science");
        navigate("Mathematics");
        assertThat(internalPlayer().accepted(first)).isNull();
        assertThat(navigate(host, first, "Algorithm").outcome()).isEqualTo("APPLIED");
        assertThat(get().me().clickCount()).isEqualTo(4);
    }

    @Test void evictedRequestMustPassCurrentEdgeValidation() {
        game = new GameService(registry, new FakeArticleProvider(), clock, 1);
        host = game.create("Host");
        code = host.view().room().roomCode();
        guest = game.join(code, "Guest");
        active(true, null);
        UUID first = UUID.randomUUID();
        navigate(host, first, "Network");
        navigate("Computer Science");
        navigate("Algorithm");
        expectCode(INVALID_NAVIGATION, () -> navigate(host, first, "Network"));
        assertThat(get().me().clickCount()).isEqualTo(3);
    }

    @Test void defaultWindowRetainsExactlyMostRecent2048AcceptedMoves() {
        active(true, null);
        UUID first = UUID.randomUUID();
        navigate(host, first, "Algorithm");
        UUID second = UUID.randomUUID();
        navigate(host, second, "Computer Science");
        for (int i = 2; i < 2048; i++) {
            clock.advance(1);
            navigate(i % 2 == 0 ? "Algorithm" : "Computer Science");
        }
        assertThat(internalPlayer().accepted(first)).isNotNull();
        navigate("Algorithm");
        assertThat(internalPlayer().accepted(first)).isNull();
        assertThat(internalPlayer().accepted(second)).isNotNull();
    }

    @Test void actionIdsAreScopedPerPlayer() {
        active(true, null);
        UUID id = UUID.randomUUID();
        assertThat(navigate(host, id, "Algorithm").outcome()).isEqualTo("APPLIED");
        assertThat(navigate(guest, id, "Algorithm").outcome()).isEqualTo("APPLIED");
    }

    @Test void rejectsInvalidActionShapesAndCapacity() {
        expectCode(INVALID_REQUEST, () -> new MoveAction(null, MoveType.BACK, null));
        expectCode(INVALID_REQUEST, () -> new MoveAction(UUID.randomUUID(), MoveType.BACK, "Algorithm"));
        expectCode(INVALID_REQUEST, () -> new MoveAction(UUID.randomUUID(), MoveType.NAVIGATE, ""));
        assertThatThrownBy(() -> new GameService(registry, new FakeArticleProvider(), clock, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static class BlockingProvider implements ArticleProvider {
        private final FakeArticleProvider delegate = new FakeArticleProvider();
        final CountDownLatch entered;
        final CountDownLatch release = new CountDownLatch(1);
        String blockedTitle;
        BlockingProvider(int calls) { entered = new CountDownLatch(calls); }
        @Override public String resolve(String title) { return delegate.resolve(title); }
        @Override public List<String> search(String query) { return delegate.search(query); }
        @Override public ArticleData loadArticle(String title) {
            if (title.equals(blockedTitle)) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test provider timed out.");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return delegate.loadArticle(title);
        }
    }

    @Test void simultaneousTargetArrivalsCommitExactlyOneWinner() throws Exception {
        var provider = new BlockingProvider(2);
        game = new GameService(registry, provider, clock, 2048);
        active(true, null);
        navigate(host, UUID.randomUUID(), "Mathematics");
        navigate(guest, UUID.randomUUID(), "Mathematics");
        provider.blockedTitle = "Quantum Mechanics";
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptTarget(host));
            var second = executor.submit(() -> attemptTarget(guest));
            try { assertThat(provider.entered.await(2, TimeUnit.SECONDS)).isTrue(); }
            finally { provider.release.countDown(); }
            assertThat(List.of(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("APPLIED", "RACE_FINISHED");
        }
        assertThat(get().room().winnerPlayerId()).isIn(host.playerId(), guest.playerId());
        assertThat(get().room().players().stream().mapToInt(PublicPlayer::clickCount).sum()).isEqualTo(3);
    }

    private String attemptTarget(SessionResponse player) {
        try { return navigate(player, UUID.randomUUID(), "Quantum Mechanics").outcome(); }
        catch (GameException e) { return e.code().name(); }
    }

    @Test void simultaneousDuplicateCommitsOneMovement() throws Exception {
        var provider = new BlockingProvider(2);
        game = new GameService(registry, provider, clock, 2048);
        active(true, null);
        provider.blockedTitle = "Algorithm";
        UUID id = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> navigate(host, id, "Algorithm").outcome());
            var second = executor.submit(() -> navigate(host, id, "Algorithm").outcome());
            try { assertThat(provider.entered.await(2, TimeUnit.SECONDS)).isTrue(); }
            finally { provider.release.countDown(); }
            assertThat(List.of(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("APPLIED", "DUPLICATE");
        }
        assertThat(get().me().clickCount()).isEqualTo(1);
        assertThat(internalPlayer().visitLog()).hasSize(2);
    }

    @Test void slowProviderDoesNotHoldRoomLockAndStaleIntentIsRejected() throws Exception {
        var provider = new BlockingProvider(1);
        game = new GameService(registry, provider, clock, 2048);
        active(true, null);
        provider.blockedTitle = "Algorithm";
        UUID id = UUID.randomUUID();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> {
                try { return navigate(host, id, "Algorithm").outcome(); }
                catch (GameException e) { return e.code().name(); }
            });
            try {
                assertThat(provider.entered.await(2, TimeUnit.SECONDS)).isTrue();
                navigate("Network");
                assertThat(get().me().currentArticle()).isEqualTo("Network");
            } finally { provider.release.countDown(); }
            assertThat(pending.get(2, TimeUnit.SECONDS)).isEqualTo("STATE_CHANGED");
        }
        assertThat(internalPlayer().accepted(id)).isNull();
        assertThat(get().me().clickCount()).isEqualTo(1);
    }

    @Test void providerFailureLeavesMovementUncommittedAndRetryable() {
        var failed = new java.util.concurrent.atomic.AtomicBoolean(true);
        ArticleProvider provider = new ArticleProvider() {
            private final FakeArticleProvider fake = new FakeArticleProvider();
            public String resolve(String title) { return fake.resolve(title); }
            public List<String> search(String query) { return fake.search(query); }
            public ArticleData loadArticle(String title) {
                if (title.equals("Algorithm") && failed.getAndSet(false)) throw new IllegalStateException("Simulated transient provider failure");
                return fake.loadArticle(title);
            }
        };
        game = new GameService(registry, provider, clock, 2048);
        active(true, null);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> navigate(host, id, "Algorithm")).isInstanceOf(IllegalStateException.class);
        assertThat(get().me().clickCount()).isZero();
        assertThat(internalPlayer().accepted(id)).isNull();
        assertThat(navigate(host, id, "Algorithm").outcome()).isEqualTo("APPLIED");
    }
}
