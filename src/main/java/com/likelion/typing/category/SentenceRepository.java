package com.likelion.typing.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SentenceRepository extends JpaRepository<Sentence, Long> {
    List<Sentence> findByCategoryIdOrderBySequenceAsc(Long categoryId);
}
