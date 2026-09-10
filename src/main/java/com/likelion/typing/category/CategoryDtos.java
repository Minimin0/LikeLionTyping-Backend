package com.likelion.typing.category;

public final class CategoryDtos {
    private CategoryDtos() {}
    public record CategoryResponse(Long id, String code, String name) {}
}
