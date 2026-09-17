package com.likelion.typing.game;

import com.likelion.typing.category.Category;
import com.likelion.typing.participant.Participant;
import com.likelion.typing.pass.PlayPass;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "game_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "participant_id")
    private Participant participant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id")
    private Category category;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "play_pass_id")
    private PlayPass playPass;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private GameSessionStatus status;
    private Long elapsedMs;
    @Column(nullable = false, updatable = false)
    private Instant startedAt;
    private Instant completedAt;
    private String invalidationReason;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public GameSession(Participant participant, Category category, PlayPass playPass) {
        this.participant = participant;
        this.category = category;
        this.playPass = playPass;
        this.status = GameSessionStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
        this.createdAt = startedAt;
        this.updatedAt = startedAt;
    }

    public void complete(long elapsedMs) {
        this.status = GameSessionStatus.COMPLETED;
        this.elapsedMs = elapsedMs;
        this.completedAt = Instant.now();
        this.updatedAt = completedAt;
    }

    public void invalidate(String reason) {
        this.status = GameSessionStatus.INVALIDATED;
        this.invalidationReason = reason;
        this.updatedAt = Instant.now();
    }
}
