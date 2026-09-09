package com.likelion.typing.game;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findFirstByParticipantIdAndStatus(Long participantId, GameSessionStatus status);
    List<GameSession> findByParticipantIdOrderByCreatedAtDesc(Long participantId);
    boolean existsByPlayPassIdAndStatusNot(Long playPassId, GameSessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameSession g join fetch g.participant join fetch g.category join fetch g.playPass where g.id = :id")
    Optional<GameSession> findByIdForUpdate(Long id);

    @Query("select min(g.elapsedMs) from GameSession g where g.participant.id = :participantId and g.category.id = :categoryId and g.status = 'COMPLETED'")
    Long findPersonalBest(Long participantId, Long categoryId);

    @Query("select g.participant.id, g.participant.nickname, min(g.elapsedMs) from GameSession g where g.category.id = :categoryId and g.status = 'COMPLETED' group by g.participant.id, g.participant.nickname order by min(g.elapsedMs), g.participant.id")
    List<Object[]> findBestRecords(Long categoryId);
}
