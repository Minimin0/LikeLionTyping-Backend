package com.likelion.typing.participant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ParticipantDtos {
    private ParticipantDtos() {}

    public record IdentifyRequest(
        @NotBlank @Size(max = 40) String nickname,
        @NotBlank @Size(max = 40) String phone
    ) {}

    public record IdentifyResponse(
        Long participantId,
        String nickname,
        boolean isNewParticipant,
        long availablePassCount
    ) {}

    public record PlayStateResponse(long availablePassCount, ActiveGameResponse activeGame) {}
    public record ActiveGameResponse(Long gameSessionId, Long categoryId) {}
}
