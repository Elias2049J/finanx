package com.elias.mismonedas.catalog.service;

import com.elias.mismonedas.catalog.dto.category.CategoryRequest;
import com.elias.mismonedas.catalog.dto.category.CategoryResponse;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(Long id, CategoryRequest request);
    CategoryResponse findById(Long id);





    Category getOwned(Long id);

    Category getOwned(Long id, Long userId);

    List<CategoryResponse> findAllByUserAndActive(Long idUser, boolean active);
    List<CategoryResponse> findAllByUser(Long idUser);
    PageResponse<CategoryResponse> list(Boolean active, TransactionType type, String search, Pageable pageable);
    void disable(Long id);

    CategoryResponse activate(Long id);
}
