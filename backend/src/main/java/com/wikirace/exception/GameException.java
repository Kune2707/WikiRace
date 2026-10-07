package com.wikirace.exception;

public class GameException extends RuntimeException {
    public enum Code {
        ROOM_NOT_FOUND, ROOM_FULL, INVALID_PLAYER_TOKEN, INVALID_DISPLAY_NAME,
        NOT_HOST, PLAYERS_NOT_READY, INVALID_RACE_STATE, INVALID_NAVIGATION,
        NO_BACK_HISTORY, RACE_FINISHED, INVALID_ROOM_SETTINGS, ARTICLE_NOT_FOUND,
        STATE_CHANGED, ACTION_ID_CONFLICT, INVALID_REQUEST, WIKIPEDIA_UNAVAILABLE, RATE_LIMITED,
        RACE_NOT_FOUND, RACE_NOT_FINISHED, RESULTS_UNAVAILABLE
    }

    private final Code code;

    public GameException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}
