package com.likelion.typing.admin;

import com.likelion.typing.game.GameSessionStatus;
import com.likelion.typing.pass.PlayPassStatus;
import com.likelion.typing.pass.PlayPassType;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record LoginRequest(@NotBlank String password) {}
    public record LoginResponse(String token, Instant expiresAt) {}
    public record PassResponse(Long id, PlayPassType type, PlayPassStatus status, Instant createdAt) {}
    public record SessionResponse(Long id, Long categoryId, Long playPassId, GameSessionStatus status,
                                  Long elapsedMs, Instant startedAt, Instant completedAt) {}
    public record ParticipantResponse(Long id, String nickname, String phone,
                                      List<PassResponse> passes, List<SessionResponse> gameSessions) {}
    public record InvalidateRequest(boolean restorePass) {}
    public record InvalidateResponse(Long gameSessionId, GameSessionStatus gameSessionStatus,
                                     Long playPassId, PlayPassStatus playPassStatus) {}
}
