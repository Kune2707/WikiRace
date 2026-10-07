package com.wikirace.game.runtime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.wikirace.game.service.GameService;

@Component
public class RaceScheduler {
    private final GameService game;
    public RaceScheduler(GameService game) { this.game = game; }

    @Scheduled(fixedDelayString = "${app.game.tick-millis:100}")
    public void advanceRaces() { game.advanceRaces(); }

    @Scheduled(fixedDelayString = "${app.game.cleanup-millis:10000}")
    public void cleanupRooms() { game.cleanupRooms(); }
}
