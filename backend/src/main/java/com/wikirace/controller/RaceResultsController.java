package com.wikirace.controller;

import java.util.UUID;
import com.wikirace.dto.RaceResults;
import com.wikirace.persistence.ResultService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/races")
public class RaceResultsController {
    private final ResultService results;
    public RaceResultsController(ResultService results) { this.results = results; }
    @GetMapping("/{id}/results")
    public ResponseEntity<RaceResults> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(results.get(id));
    }
}
