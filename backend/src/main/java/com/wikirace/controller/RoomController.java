package com.wikirace.controller;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.wikirace.dto.GameDtos.*;
import com.wikirace.exception.GameException;
import com.wikirace.game.model.MoveAction;
import com.wikirace.game.model.MoveType;
import com.wikirace.game.model.SettingsPatch;
import com.wikirace.game.service.GameService;
import static com.wikirace.exception.GameException.Code.INVALID_REQUEST;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final GameService game;
    public RoomController(GameService game) { this.game = game; }
    public record NameRequest(String displayName) {}
    public record ReadyRequest(@NotNull Boolean ready) {}
    public record NavigateRequest(@NotNull UUID actionId, @NotBlank String destinationArticle) {}
    public record BackRequest(@NotNull UUID actionId) {}

    @PostMapping
    public ResponseEntity<SessionResponse> create(@RequestBody NameRequest request) {
        var response = game.create(request.displayName());
        return ResponseEntity.created(URI.create("/api/rooms/" + response.view().room().roomCode()))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<SessionResponse> join(@PathVariable String code, @RequestBody NameRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(game.join(code, request.displayName()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<RoomView> get(@PathVariable String code,
            @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return ok(game.get(code, token));
    }

    @PatchMapping("/{code}/settings")
    public ResponseEntity<RoomView> settings(@PathVariable String code,
            @RequestHeader(value = "X-Player-Token", required = false) String token, @RequestBody JsonNode body) {
        requireFields(body, Set.of("startArticle", "targetArticle", "unlimited", "timeLimitSeconds"));
        Boolean unlimited = null;
        if (body.has("unlimited")) {
            if (!body.get("unlimited").isBoolean()) throw invalid();
            unlimited = body.get("unlimited").booleanValue();
        }
        Integer duration = null;
        if (body.hasNonNull("timeLimitSeconds")) {
            var node = body.get("timeLimitSeconds");
            if (!node.isIntegralNumber() || !node.canConvertToInt()) throw invalid();
            duration = node.intValue();
        }
        var patch = new SettingsPatch(body.has("startArticle"), text(body, "startArticle"),
                body.has("targetArticle"), text(body, "targetArticle"), unlimited, body.has("timeLimitSeconds"), duration);
        return ok(game.configure(code, token, patch));
    }

    @PostMapping("/{code}/ready")
    public ResponseEntity<RoomView> ready(@PathVariable String code,
            @RequestHeader(value = "X-Player-Token", required = false) String token,
            @Valid @RequestBody ReadyRequest request) {
        return ok(game.ready(code, token, request.ready()));
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<RoomView> start(@PathVariable String code,
            @RequestHeader(value = "X-Player-Token", required = false) String token, @RequestBody JsonNode body) {
        requireFields(body, Set.of());
        return ok(game.start(code, token));
    }

    @PostMapping("/{code}/actions/navigate")
    public ResponseEntity<ActionResponse> navigate(@PathVariable String code,
            @RequestHeader(value = "X-Player-Token", required = false) String token,
            @Valid @RequestBody NavigateRequest request) {
        return ok(game.move(code, token, new MoveAction(request.actionId(), MoveType.NAVIGATE, request.destinationArticle())));
    }

    @PostMapping("/{code}/actions/back")
    public ResponseEntity<ActionResponse> back(@PathVariable String code,
            @RequestHeader(value = "X-Player-Token", required = false) String token,
            @Valid @RequestBody BackRequest request) {
        return ok(game.move(code, token, new MoveAction(request.actionId(), MoveType.BACK, null)));
    }

    private static <T> ResponseEntity<T> ok(T response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    private static void requireFields(JsonNode body, Set<String> allowed) {
        if (!body.isObject()) throw invalid();
        body.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw invalid(); });
    }

    private static String text(JsonNode body, String key) {
        if (!body.hasNonNull(key)) return null;
        if (!body.get(key).isTextual()) throw invalid();
        return body.get(key).textValue();
    }

    private static GameException invalid() { return new GameException(INVALID_REQUEST, "Invalid request fields or types."); }
}
