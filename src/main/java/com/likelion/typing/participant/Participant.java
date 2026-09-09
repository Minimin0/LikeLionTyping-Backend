package com.likelion.typing.participant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String nickname;
    @Column(nullable = false, unique = true, length = 30)
    private String phone;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Participant(String nickname, String phone) {
        this.nickname = nickname;
        this.phone = phone;
        this.createdAt = Instant.now();
    }
}
