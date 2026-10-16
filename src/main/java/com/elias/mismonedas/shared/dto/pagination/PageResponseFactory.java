package com.elias.mismonedas.shared.dto.pagination;

import org.springframework.data.domain.Page;

import java.util.function.Function;

public final class PageResponseFactory {

    private PageResponseFactory() {
    }

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                new PaginationResponse(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                )
        );
    }
}
