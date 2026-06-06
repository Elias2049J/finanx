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
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
@Service

@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = categoryMapper.toEntity(request);
        category.setUser(userRepository.getReferenceById(request.getUserId()));
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    @Override
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category existing = categoryRepository.findById(id).orElseThrow();
        categoryMapper.updateFromDto(request, existing);
        return categoryMapper.toResponse(categoryRepository.save(existing));
    }

    @Transactional(readOnly = true)
    @Override
    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(
                categoryRepository.findById(id).orElseThrow()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryResponse> findAllByUser(Long idUser) {
        return categoryRepository.findAllByUser_Id(idUser)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryResponse> findAllByUserAndActive(Long idUser, boolean active) {
        return categoryRepository.findAllByUser_IdAndActive(idUser, active)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public void disable(Long id) {
        Category existing = categoryRepository.findById(id).orElseThrow();
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setActive(false);
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        categoryRepository.save(existing);
    }

    @Transactional
    @Override
    public CategoryResponse activate(Long id) {
        Category existing = categoryRepository.findById(id).orElseThrow();
        existing.setActive(true);
        return categoryMapper.toResponse(categoryRepository.save(existing));
    }
}
