package com.likelion.typing.common.exception;

import com.likelion.typing.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ErrorResponse> handle(AppException exception, HttpServletRequest request) {
        var code = exception.code();
        return ResponseEntity.status(code.status())
            .body(new ErrorResponse(code.name(), code.message(), Instant.now(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(HttpServletRequest request) {
        var code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.status())
            .body(new ErrorResponse(code.name(), code.message(), Instant.now(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(HttpServletRequest request) {
        var code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
            .body(new ErrorResponse(code.name(), code.message(), Instant.now(), request.getRequestURI()));
    }
}
