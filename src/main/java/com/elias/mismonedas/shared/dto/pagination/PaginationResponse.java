package com.elias.mismonedas.shared.dto.pagination;

public record PaginationResponse(int page, int size, long totalItems, int totalPages) {
}
