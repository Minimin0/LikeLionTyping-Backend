package com.likelion.typing.participant;

import com.likelion.typing.pass.PlayPass;
import com.likelion.typing.pass.PlayPassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ParticipantCreator {
    private final ParticipantRepository participants;
    private final PlayPassRepository passes;
    private final JdbcTemplate jdbc;

    ParticipantCreator(ParticipantRepository participants, PlayPassRepository passes, JdbcTemplate jdbc) {
        this.participants = participants;
        this.passes = passes;
        this.jdbc = jdbc;
    }

    @Transactional
    Creation create(String nickname, String phone) {
        // ponytail: one row serializes first-time registration; shard only if registration throughput proves it necessary.
        jdbc.queryForObject("SELECT id FROM participant_creation_lock WHERE id = 1 FOR UPDATE", Integer.class);
        var existing = participants.findByPhone(phone);
        if (existing.isPresent()) return new Creation(existing.get(), false);
        var participant = participants.saveAndFlush(new Participant(nickname, phone));
        passes.save(PlayPass.free(participant));
        return new Creation(participant, true);
    }

    record Creation(Participant participant, boolean created) {}
}
