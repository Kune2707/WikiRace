package com.wikirace.game.model;

public record RaceSettings(String startArticle, String targetArticle, boolean unlimited, Integer timeLimitSeconds) {
    public static RaceSettings unconfigured() {
        return new RaceSettings(null, null, true, null);
    }
}
