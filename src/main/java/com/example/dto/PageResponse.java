package com.example.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Стабильный JSON для постраничных ответов: content, page, size, totalElements, totalPages.
 * (Прямая сериализация PageImpl не гарантирует стабильную структуру JSON - Spring Data предупреждает об этом.)
 */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages,
                              boolean first,
                              boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
