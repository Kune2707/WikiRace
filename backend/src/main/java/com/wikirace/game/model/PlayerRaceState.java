package com.wikirace.game.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import com.wikirace.wikipedia.ArticleData;

public class PlayerRaceState {
    public record AcceptedAction(UUID actionId, String fingerprint, String roomCode, long acceptedVersion) {}

    private final UUID id = UUID.randomUUID();
    private final String displayName;
    private final byte[] tokenDigest;
    private final int actionCapacity;
    private final LinkedHashMap<UUID, AcceptedAction> acceptedActions = new LinkedHashMap<>();
    private boolean ready;
    private final java.util.Set<String> sessions = new java.util.HashSet<>();
    private ArticleData article;
    private NavigationHistory history;
    private final List<String> visitLog = new ArrayList<>();
    private int clickCount;
    private long movementRevision;
    private boolean closeToTarget;
    private java.time.Instant disconnectedAt;
    private final java.util.ArrayDeque<java.time.Instant> movementRequests = new java.util.ArrayDeque<>();

    public java.time.Instant disconnectedAt() { return disconnectedAt; }
    public void disconnectedAt(java.time.Instant now) { disconnectedAt = now; }
    public void requireMovementBudget(java.time.Instant now, int capacity) {
        while (!movementRequests.isEmpty() && !movementRequests.getFirst().isAfter(now.minusSeconds(1))) movementRequests.removeFirst();
        if (movementRequests.size() >= capacity) throw new com.wikirace.exception.GameException(
                com.wikirace.exception.GameException.Code.RATE_LIMITED, "Too many movement requests. Wait a moment and retry.");
        movementRequests.addLast(now);
    }

    public PlayerRaceState(String displayName, byte[] tokenDigest, int actionCapacity) {
        this.displayName = displayName;
        this.tokenDigest = tokenDigest.clone();
        this.actionCapacity = actionCapacity;
    }

    public UUID id() { return id; }
    public String displayName() { return displayName; }
    public byte[] tokenDigest() { return tokenDigest.clone(); }
    public boolean ready() { return ready; }
    public boolean connected() { return !sessions.isEmpty(); }
    public boolean connect(String session) { return sessions.add(session); }
    public boolean disconnect(String session) { return sessions.remove(session); }
    public void setReady(boolean ready) { this.ready = ready; }
    public ArticleData article() { return article; }
    public NavigationHistory history() { return history; }
    public List<String> visitLog() { return List.copyOf(visitLog); }
    public int clickCount() { return clickCount; }
    public long movementRevision() { return movementRevision; }
    public boolean closeToTarget() { return closeToTarget; }
    public AcceptedAction accepted(UUID actionId) { return acceptedActions.get(actionId); }

    public void initialize(ArticleData start, String target) {
        article = start;
        history = new NavigationHistory(start.title());
        visitLog.add(start.title());
        closeToTarget = start.outgoingLinks().contains(target);
    }

    public void move(MoveType type, ArticleData destination, String target) {
        if (type == MoveType.BACK) history.back();
        else history.navigate(destination.title());
        article = destination;
        clickCount++;
        movementRevision++;
        visitLog.add(destination.title());
        closeToTarget = destination.outgoingLinks().contains(target);
    }

    public void remember(MoveAction action, String roomCode, long version) {
        acceptedActions.put(action.actionId(), new AcceptedAction(action.actionId(), action.fingerprint(), roomCode, version));
        if (acceptedActions.size() > actionCapacity) acceptedActions.remove(acceptedActions.firstEntry().getKey());
    }
}
