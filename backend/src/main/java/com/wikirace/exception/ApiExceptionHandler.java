package com.wikirace.exception;

import java.time.Clock;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String code, String message, Instant timestamp) {}
    private final Clock clock;
    public ApiExceptionHandler(Clock clock) { this.clock = clock; }

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ApiError> game(GameException exception) {
        int status = switch (exception.code()) {
            case INVALID_REQUEST, INVALID_DISPLAY_NAME, INVALID_ROOM_SETTINGS -> 400;
            case INVALID_PLAYER_TOKEN -> 401;
            case NOT_HOST -> 403;
            case ROOM_NOT_FOUND, ARTICLE_NOT_FOUND, RACE_NOT_FOUND -> 404;
            case INVALID_NAVIGATION -> 422;
            case WIKIPEDIA_UNAVAILABLE, RESULTS_UNAVAILABLE -> 503;
            case RATE_LIMITED -> 429;
            default -> 409;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.code().name(), exception.getMessage(), clock.instant()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Invalid request fields or types.", clock.instant()));
    }
}
