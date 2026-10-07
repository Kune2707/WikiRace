package com.wikirace.controller;

import java.util.Map;
import com.wikirace.dto.ArticleResponse;
import com.wikirace.game.service.GameService;
import com.wikirace.exception.GameException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlayerArticleController {
    private final GameService games;
    public PlayerArticleController(GameService games) { this.games = games; }

    @GetMapping("/api/rooms/{code}/me/article")
    public ResponseEntity<ArticleResponse> article(@PathVariable String code,
            @RequestHeader(value = "X-Player-Token", required = false) String token,
            @RequestParam Map<String, String> parameters) {
        if (!parameters.isEmpty()) throw new GameException(GameException.Code.INVALID_REQUEST, "Article selection is server-controlled.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(games.currentArticle(code, token));
    }
}
