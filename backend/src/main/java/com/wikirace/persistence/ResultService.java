package com.wikirace.persistence;

import java.util.UUID;
import com.wikirace.dto.RaceResults;
import com.wikirace.game.service.GameService;
import com.wikirace.exception.GameException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import static com.wikirace.exception.GameException.Code.*;

@Service
public class ResultService {
    private final GameService game;
    private final ObjectProvider<ResultStore> stores;
    public ResultService(GameService game, ObjectProvider<ResultStore> stores) { this.game = game; this.stores = stores; }
    public RaceResults get(UUID id) {
        var retained = game.completedResult(id);
        if (retained.isPresent()) return retained.get();
        ResultStore store = stores.getIfAvailable();
        if (store == null) throw new GameException(RACE_NOT_FOUND, "Race result not found.");
        java.util.Optional<RaceResults> saved;
        try { saved = store.read(id); }
        catch (RuntimeException failure) { throw new GameException(RESULTS_UNAVAILABLE, "Saved race results are temporarily unavailable."); }
        return saved.orElseThrow(() -> new GameException(RACE_NOT_FOUND, "Race result not found."));
    }
}
