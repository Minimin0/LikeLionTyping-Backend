package com.likelion.typing.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findByParticipantIdOrderByCreatedAtDesc(Long participantId);

    @Query("select p from PaymentRecord p join fetch p.participant order by p.createdAt desc, p.id desc")
    List<PaymentRecord> findAllWithParticipantOrderByCreatedAtDesc();

    @Query("select coalesce(sum(p.amountKrw), 0) from PaymentRecord p")
    long totalAmountKrw();

    @Query("select coalesce(sum(p.amountKrw), 0) from PaymentRecord p where p.participant.id = :participantId")
    long totalAmountKrwByParticipantId(Long participantId);
}
