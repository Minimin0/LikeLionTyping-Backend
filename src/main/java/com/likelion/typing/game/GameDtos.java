package com.likelion.typing.game;

import com.likelion.typing.category.CategoryDtos;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public final class GameDtos {
    private GameDtos() {}

    public record StartRequest(@NotNull Long participantId, @NotNull Long categoryId) {}
    public record SentenceResponse(int sequence, String content) {}
    public record StartResponse(Long gameSessionId, CategoryDtos.CategoryResponse category, List<SentenceResponse> sentences,
                                boolean resumedExisting, boolean passConsumed, long availablePassCount) {}
    public record CompleteRequest(@NotNull @Positive Long elapsedMs) {}
    public record ResultResponse(Long gameSessionId, GameSessionStatus status, Long elapsedMs,
                                 Long personalBestMs, boolean personalBest, Integer rank) {}
}
