package com.likelion.typing.participant;

import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import com.likelion.typing.game.GameSessionRepository;
import com.likelion.typing.game.GameSessionStatus;
import com.likelion.typing.pass.PlayPassRepository;
import com.likelion.typing.pass.PlayPassStatus;
import org.springframework.stereotype.Service;

@Service
public class ParticipantService {
    private final ParticipantRepository participants;
    private final PlayPassRepository passes;
    private final GameSessionRepository games;
    private final ParticipantCreator creator;

    public ParticipantService(ParticipantRepository participants, PlayPassRepository passes,
                              GameSessionRepository games, ParticipantCreator creator) {
        this.participants = participants;
        this.passes = passes;
        this.games = games;
        this.creator = creator;
    }

    public ParticipantDtos.IdentifyResponse identify(ParticipantDtos.IdentifyRequest request) {
        var phone = request.phone().replaceAll("[-\\s]", "");
        var nickname = request.nickname().trim();
        if (phone.isBlank() || nickname.isBlank()) throw new AppException(ErrorCode.VALIDATION_ERROR);
        if (!phone.matches("^010\\d{8}$")) throw new AppException(ErrorCode.VALIDATION_ERROR);

        var existing = participants.findByPhone(phone);
        if (existing.isPresent()) return response(existing.get(), nickname, false);

        var creation = creator.create(nickname, phone);
        return response(creation.participant(), nickname, creation.created());
    }

    public ParticipantDtos.PlayStateResponse playState(Long participantId) {
        var participant = participants.findById(participantId)
            .orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND));
        var activeGame = games.findFirstByParticipantIdAndStatus(participant.getId(), GameSessionStatus.IN_PROGRESS)
            .map(game -> new ParticipantDtos.ActiveGameResponse(game.getId(), game.getCategory().getId()))
            .orElse(null);
        return new ParticipantDtos.PlayStateResponse(
            passes.countByParticipantIdAndStatus(participant.getId(), PlayPassStatus.AVAILABLE),
            activeGame);
    }

    private ParticipantDtos.IdentifyResponse response(Participant participant, String nickname, boolean isNew) {
        if (!participant.getNickname().equals(nickname)) throw new AppException(ErrorCode.NICKNAME_MISMATCH);
        return new ParticipantDtos.IdentifyResponse(
            participant.getId(), participant.getNickname(), isNew,
            passes.countByParticipantIdAndStatus(participant.getId(), PlayPassStatus.AVAILABLE));
    }
}
