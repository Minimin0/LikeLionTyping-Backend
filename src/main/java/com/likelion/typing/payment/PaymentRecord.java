package com.likelion.typing.payment;

import com.likelion.typing.participant.Participant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payment_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private int amountKrw;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentRecord(Participant participant, int quantity, int amountKrw) {
        this.participant = participant;
        this.quantity = quantity;
        this.amountKrw = amountKrw;
        this.createdAt = Instant.now();
    }
}
