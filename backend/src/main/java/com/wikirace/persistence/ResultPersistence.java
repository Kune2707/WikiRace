package com.wikirace.persistence;

import com.wikirace.game.service.GameService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ResultPersistence {
    private final ObjectProvider<ResultStore> stores;
    private final GameService game;
    public ResultPersistence(ObjectProvider<ResultStore> stores, GameService game) { this.stores = stores; this.game = game; }
    @EventListener public void completed(RaceCompleted event) {
        String status = "FAILED";
        ResultStore store = stores.getIfAvailable();
        if (store != null) {
            try { store.save(event.result()); status = "SAVED"; }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(ResultPersistence.class).warn("Completed race {} could not be persisted; winner remains committed.", event.result().id());
            }
        }
        game.persistenceStatus(event.result().id(), status);
    }
}
