package com.onesley.oneclick.shared;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * DTO de réponse paginée standardisée pour l'API REST.
 *
 * <p>Plus léger que {@link org.springframework.data.domain.Page} (qui leak des
 * détails Pageable/Sort en JSON). Records immutables.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext()
        );
    }
}
