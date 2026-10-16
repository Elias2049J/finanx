package com.elias.mismonedas.shared.dto.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationDefaults {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private PaginationDefaults() {
    }

    public static Pageable stablePage(Pageable pageable, Sort defaultSort) {
        int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
        int requestedSize = pageable.isPaged() ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
        int size = Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return PageRequest.of(pageNumber, size, sort);
    }
}
