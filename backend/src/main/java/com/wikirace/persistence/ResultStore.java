package com.wikirace.persistence;

import java.util.Optional;
import java.util.UUID;
import com.wikirace.dto.RaceResults;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test | persistence-test")
public class ResultStore {
    private final CompletedRaceRepository races;
    private final EntityManager entities;
    public ResultStore(CompletedRaceRepository races, EntityManager entities) { this.races = races; this.entities = entities; }
    @Transactional
    public void save(RaceResults result) {
        if (races.claim(result) == 0) return;
        var race = entities.getReference(CompletedRaceEntity.class, result.id());
        for (int index = 0; index < result.players().size(); index++) {
            entities.persist(new PlayerResultEntity(race, result.players().get(index), index));
        }
        entities.flush();
    }
    @Transactional(readOnly = true)
    public Optional<RaceResults> read(UUID id) { return races.findById(id).map(CompletedRaceEntity::result); }
}
