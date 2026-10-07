package com.wikirace.game.runtime;

import java.time.Duration;

public record GameLimits(int movementPerSecond, Duration lobbyGrace, Duration finishedRetention) {
    public static GameLimits defaults() { return new GameLimits(10, Duration.ofMinutes(5), Duration.ofMinutes(30)); }
    public GameLimits {
        if (movementPerSecond < 1 || lobbyGrace.isNegative() || lobbyGrace.isZero() ||
                finishedRetention.isNegative() || finishedRetention.isZero()) {
            throw new IllegalArgumentException("Game limits must be positive.");
        }
    }
}
