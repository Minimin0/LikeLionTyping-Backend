package com.likelion.typing.participant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    Optional<Participant> findByPhone(String phone);
    List<Participant> findAllByOrderByCreatedAtDescIdDesc();
    List<Participant> findByNicknameContainingIgnoreCaseOrderByCreatedAtDesc(String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.id = :id")
    Optional<Participant> findByIdForUpdate(Long id);
}
