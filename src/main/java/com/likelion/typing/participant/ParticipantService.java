package com.likelion.typing.participant;

import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import com.likelion.typing.pass.PlayPassRepository;
import com.likelion.typing.pass.PlayPassStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ParticipantService {
    private final ParticipantRepository participants;
    private final PlayPassRepository passes;
    private final ParticipantCreator creator;

    public ParticipantService(ParticipantRepository participants, PlayPassRepository passes, ParticipantCreator creator) {
        this.participants = participants;
        this.passes = passes;
        this.creator = creator;
    }

    public ParticipantDtos.IdentifyResponse identify(ParticipantDtos.IdentifyRequest request) {
        var phone = request.phone().replaceAll("[-\\s]", "");
        var nickname = request.nickname().trim();
        if (phone.isBlank() || nickname.isBlank()) throw new AppException(ErrorCode.VALIDATION_ERROR);

        var existing = participants.findByPhone(phone);
        if (existing.isPresent()) return response(existing.get(), nickname, false);

        try {
            return response(creator.create(nickname, phone), nickname, true);
        } catch (DataIntegrityViolationException race) {
            var participant = participants.findByPhone(phone).orElseThrow(() -> race);
            return response(participant, nickname, false);
        }
    }

    private ParticipantDtos.IdentifyResponse response(Participant participant, String nickname, boolean isNew) {
        if (!participant.getNickname().equals(nickname)) throw new AppException(ErrorCode.NICKNAME_MISMATCH);
        return new ParticipantDtos.IdentifyResponse(
            participant.getId(), participant.getNickname(), isNew,
            passes.countByParticipantIdAndStatus(participant.getId(), PlayPassStatus.AVAILABLE));
    }
}
