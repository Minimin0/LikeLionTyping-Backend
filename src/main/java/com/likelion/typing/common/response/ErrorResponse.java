package com.likelion.typing.common.response;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp, String path) {}
