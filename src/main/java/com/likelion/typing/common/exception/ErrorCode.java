package com.likelion.typing.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request"),
    NICKNAME_MISMATCH(HttpStatus.CONFLICT, "Nickname does not match"),
    NO_AVAILABLE_PASS(HttpStatus.CONFLICT, "No available play pass"),
    INVALID_GAME_STATE(HttpStatus.CONFLICT, "Game session is not in a valid state"),
    ACTIVE_GAME_EXISTS(HttpStatus.CONFLICT, "Another game is already in progress"),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Participant not found"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category not found"),
    GAME_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Game session not found"),
    INVALID_ELAPSED_TIME(HttpStatus.BAD_REQUEST, "Elapsed time must be positive"),
    SENTENCE_CONTENT_INVALID(HttpStatus.CONFLICT, "Category must contain exactly five sentences"),
    ADMIN_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Admin authentication failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
