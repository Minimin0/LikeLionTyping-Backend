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
                                  Long elapsedMs, Instant startedAt, Instant completedAt, String invalidationReason) {}
    public record PaymentResponse(Long id, int quantity, int amountKrw, Instant createdAt) {}
    public record PaymentHistoryItemResponse(Long id, Long participantId, String nickname, String phone,
                                             int quantity, int amountKrw, Instant createdAt) {}
    public record PaymentHistoryResponse(long totalPaymentAmountKrw, long totalPaymentCount,
                                         long totalPaidPassQuantity,
                                         List<PaymentHistoryItemResponse> payments) {}
    public record ParticipantHistoryItemResponse(Long id, String nickname, String phone, Instant createdAt) {}
    public record ParticipantHistoryResponse(long totalParticipants,
                                             List<ParticipantHistoryItemResponse> participants) {}
    public record CategoryBestResponse(String categoryCode, Long elapsedMs) {}
    public record ParticipantSummary(boolean freeParticipationUsed, long availablePassCount,
                                     long availablePaidPassCount, long totalPlayCount,
                                     long completedGameCount, long invalidatedGameCount,
                                     long totalPaymentAmountKrw, List<CategoryBestResponse> bestRecords) {}
    public record ParticipantResponse(Long id, String nickname, String phone,
                                      List<PassResponse> passes, List<SessionResponse> gameSessions,
                                      List<PaymentResponse> payments, ParticipantSummary summary) {}
    public record IssuePassRequest(Integer quantity) {}
    public record IssuePassResponse(int quantity, int amountKrw, long availablePaidPassCount,
                                    PaymentResponse payment, List<PassResponse> passes) {}
    public record DashboardResponse(long totalParticipants, long totalPlayCount, long freePlayCount,
                                    long paidPlayCount, long totalPaymentAmountKrw,
                                    long availablePaidPassCount, long ch01PlayCount,
                                    long ch02PlayCount, long ch03PlayCount,
                                    long completedGameCount, long invalidatedGameCount) {}
    public record InvalidateRequest(String reason, boolean restorePass) {}
    public record InvalidateResponse(Long gameSessionId, GameSessionStatus gameSessionStatus,
                                     Long playPassId, PlayPassStatus playPassStatus) {}
}
