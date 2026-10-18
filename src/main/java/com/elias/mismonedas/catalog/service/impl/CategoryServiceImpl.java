package com.elias.mismonedas.catalog.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.dto.category.CategoryRequest;
import com.elias.mismonedas.catalog.dto.category.CategoryResponse;
import com.elias.mismonedas.catalog.mapper.CategoryMapper;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.CategorySpecifications;
import com.elias.mismonedas.catalog.service.CategoryService;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service

@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final SecurityService securityService;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = categoryMapper.toEntity(request);
        category.setUser(securityService.getCurrentUser());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    @Override
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category existing = getOwned(id);
        categoryMapper.updateFromDto(request, existing);
        return categoryMapper.toResponse(categoryRepository.save(existing));
    }

    @Transactional(readOnly = true)
    @Override
    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(getOwned(id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryResponse> findAllByUser(Long idUser) {
        return categoryRepository.findAllByUser_Id(idUser)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(Boolean active, TransactionType type, String search, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "name"));
        Page<Category> page = categoryRepository.findAll(
                CategorySpecifications.list(userId, active, type, search), stablePage);
        return PageResponseFactory.from(page, categoryMapper::toResponse);
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
        Category existing = getOwned(id);
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setActive(false);
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        categoryRepository.save(existing);
    }

    @Transactional
    @Override
    public CategoryResponse activate(Long id) {
        Category existing = getOwned(id);
        existing.setActive(true);
        return categoryMapper.toResponse(categoryRepository.save(existing));
    }

    @Override
    @Transactional(readOnly = true)
    public Category getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return categoryRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
    }
}
