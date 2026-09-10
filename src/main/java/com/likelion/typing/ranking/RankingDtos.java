package com.likelion.typing.ranking;

public final class RankingDtos {
    private RankingDtos() {}
    public record RankingResponse(int rank, String nickname, long elapsedMs) {}
}
