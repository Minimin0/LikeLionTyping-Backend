package com.likelion.typing.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) { this.categories = categories; }

    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryResponse> findAll() {
        return categories.findAllByOrderByCodeAsc().stream()
            .map(category -> new CategoryDtos.CategoryResponse(category.getId(), category.getCode(), category.getName()))
            .toList();
    }
}
