package com.elias.finanx.service;

import com.elias.finanx.dto.category.CategoryRequest;
import com.elias.finanx.dto.category.CategoryResponse;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(Long id, CategoryRequest request);
    CategoryResponse findById(Long id);
    List<CategoryResponse> findAllByUserAndActive(Long idUser, boolean active);
    List<CategoryResponse> findAllByUser(Long idUser);
    void disable(Long id);

    CategoryResponse activate(Long id);
}
