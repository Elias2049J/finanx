package com.elias.finanx.service.impl;

import com.elias.finanx.dto.category.CategoryRequest;
import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.entity.Category;
import com.elias.finanx.mapper.CategoryMapper;
import com.elias.finanx.repository.CategoryRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@Service

@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public CategoryResponse create(CategoryRequest request) {
        Category category = categoryMapper.toEntity(request);
        category.setUser(userRepository.getReferenceById(request.getUserId()));
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category existing = categoryRepository.findById(id).orElseThrow();
        categoryMapper.updateFromDto(request, existing);
        return categoryMapper.toResponse(categoryRepository.save(existing));
    }

    @Override
    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(
                categoryRepository.findById(id).orElseThrow()
        );
    }

    @Override
    public List<CategoryResponse> findAllByUser(Long idUser) {
        return categoryRepository.findAllByUser_Id(idUser)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public List<CategoryResponse> findAllByUserAndActive(Long idUser, boolean active) {
        return categoryRepository.findAllByUser_IdAndActive(idUser, active)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public void disable(Long id) {
        Category existing = categoryRepository.findById(id).orElseThrow();
        existing.setActive(false);
        categoryRepository.save(existing);
    }

    @Override
    public CategoryResponse activate(Long id) {
        Category existing = categoryRepository.findById(id).orElseThrow();
        existing.setActive(true);
        return categoryMapper.toResponse(categoryRepository.save(existing));
    }
}
