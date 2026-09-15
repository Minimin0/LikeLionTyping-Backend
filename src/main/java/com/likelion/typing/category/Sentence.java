package com.likelion.typing.category;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentences", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "sequence_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sentence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @Column(name = "sequence_number", nullable = false)
    private int sequence;
    @Column(nullable = false, length = 500)
    private String content;

    public Sentence(Category category, int sequence, String content) {
        this.category = category;
        this.sequence = sequence;
        this.content = content;
    }
}
