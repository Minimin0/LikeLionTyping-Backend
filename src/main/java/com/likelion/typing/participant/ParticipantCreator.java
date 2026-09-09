package com.likelion.typing.participant;

import com.likelion.typing.pass.PlayPass;
import com.likelion.typing.pass.PlayPassRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ParticipantCreator {
    private final ParticipantRepository participants;
    private final PlayPassRepository passes;

    ParticipantCreator(ParticipantRepository participants, PlayPassRepository passes) {
        this.participants = participants;
        this.passes = passes;
    }

    @Transactional
    Participant create(String nickname, String phone) {
        var participant = participants.saveAndFlush(new Participant(nickname, phone));
        passes.save(PlayPass.free(participant));
        return participant;
    }
}
