package com.wikirace.game.model;

import java.util.UUID;
import com.wikirace.exception.GameException;
import static com.wikirace.exception.GameException.Code.INVALID_REQUEST;

public record MoveAction(UUID actionId, MoveType type, String destinationArticle) {
    public MoveAction {
        if (actionId == null || type == null ||
                (type == MoveType.NAVIGATE && (destinationArticle == null || destinationArticle.isBlank())) ||
                (type == MoveType.BACK && destinationArticle != null)) {
            throw new GameException(INVALID_REQUEST, "Invalid movement intent.");
        }
    }

    public String fingerprint() {
        return type.name() + ":" + (destinationArticle == null ? "" : destinationArticle);
    }
}
