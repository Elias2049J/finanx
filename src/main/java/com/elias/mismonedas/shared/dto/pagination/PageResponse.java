package com.elias.mismonedas.shared.dto.pagination;

import java.util.List;

public record PageResponse<T>(List<T> items, PaginationResponse pagination) {
}
