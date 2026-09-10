package com.likelion.typing.pass;

import com.likelion.typing.participant.Participant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "play_passes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayPass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;
    @Column(name = "free_participant_id", unique = true)
    private Long freeParticipantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private PlayPassType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private PlayPassStatus status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public static PlayPass free(Participant participant) {
        return new PlayPass(participant, PlayPassType.FREE, participant.getId());
    }

    public static PlayPass paid(Participant participant) {
        return new PlayPass(participant, PlayPassType.PAID, null);
    }

    private PlayPass(Participant participant, PlayPassType type, Long freeParticipantId) {
        this.participant = participant;
        this.type = type;
        this.freeParticipantId = freeParticipantId;
        this.status = PlayPassStatus.AVAILABLE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void consume() {
        this.status = PlayPassStatus.CONSUMED;
        this.updatedAt = Instant.now();
    }

    public void restore() {
        this.status = PlayPassStatus.AVAILABLE;
        this.updatedAt = Instant.now();
    }
}
