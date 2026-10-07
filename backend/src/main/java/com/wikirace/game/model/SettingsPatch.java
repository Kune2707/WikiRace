package com.wikirace.game.model;

public record SettingsPatch(boolean hasStart, String startArticle, boolean hasTarget, String targetArticle,
                            Boolean unlimited, boolean hasDuration, Integer timeLimitSeconds) {
    public RaceSettings merge(RaceSettings existing) {
        return new RaceSettings(hasStart ? startArticle : existing.startArticle(),
                hasTarget ? targetArticle : existing.targetArticle(),
                unlimited == null ? existing.unlimited() : unlimited,
                hasDuration ? timeLimitSeconds : existing.timeLimitSeconds());
    }
}
