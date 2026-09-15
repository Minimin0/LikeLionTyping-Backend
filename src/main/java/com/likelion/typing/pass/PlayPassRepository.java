package com.likelion.typing.pass;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlayPassRepository extends JpaRepository<PlayPass, Long> {
    long countByParticipantIdAndStatus(Long participantId, PlayPassStatus status);
    List<PlayPass> findByParticipantIdOrderByCreatedAtAsc(Long participantId);
    Optional<PlayPass> findFirstByParticipantIdAndTypeAndStatusOrderByCreatedAtAsc(
        Long participantId, PlayPassType type, PlayPassStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlayPass p where p.participant.id = :participantId and p.type = :type and p.status = :status order by p.createdAt, p.id")
    List<PlayPass> findAvailableForUpdate(Long participantId, PlayPassType type, PlayPassStatus status);
}
