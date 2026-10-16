package com.elias.mismonedas.catalog.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.dto.reason.ReasonRequest;
import com.elias.mismonedas.catalog.dto.reason.ReasonResponse;
import com.elias.mismonedas.catalog.mapper.ReasonMapper;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.catalog.repository.ReasonSpecifications;
import com.elias.mismonedas.catalog.service.ReasonService;
import com.elias.mismonedas.identity.security.SecurityService;
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
public class ReasonServiceImpl implements ReasonService {
    private final ReasonMapper reasonMapper;
    private final ReasonRepository rRepository;
    private final SecurityService securityService;

    @Override
    @Transactional
    public ReasonResponse create(ReasonRequest request) {
        Reason reason = reasonMapper.toEntity(request);
        reason.setUser(securityService.getCurrentUser());
        return reasonMapper.toResponse(rRepository.save(reason));
    }

    @Override
    @Transactional
    public ReasonResponse update(Long id, ReasonRequest request) {
        Reason existing = getOwned(id);
        reasonMapper.updateFromDto(request, existing);
        return reasonMapper.toResponse(rRepository.save(existing));
    }

    @Override
    public ReasonResponse findById(Long id) {
        return reasonMapper.toResponse(getOwned(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReasonResponse> findAllByUser(Long idUser) {
        return rRepository.findAllByUser_IdAndActiveTrue(idUser)
                .stream()
                .map(reasonMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReasonResponse> list(Boolean active, String search, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "description"));
        Page<Reason> page = rRepository.findAll(ReasonSpecifications.list(userId, active, search), stablePage);
        return PageResponseFactory.from(page, reasonMapper::toResponse);
    }

    @Transactional
    @Override
    public void disable(Long id) {
        Reason existing = getOwned(id);
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setActive(false);
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        rRepository.save(existing);
    }

    private Reason getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return rRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Reason not found: " + id));
    }
}
