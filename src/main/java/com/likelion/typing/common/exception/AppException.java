package com.likelion.typing.common.exception;

public class AppException extends RuntimeException {
    private final ErrorCode code;

    public AppException(ErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public ErrorCode code() { return code; }
}
