package com.elias.mismonedas.catalog.service;

import com.elias.mismonedas.catalog.dto.reason.ReasonRequest;
import com.elias.mismonedas.catalog.dto.reason.ReasonResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReasonService {
    ReasonResponse create(ReasonRequest request);

    ReasonResponse update(Long id, ReasonRequest request);

    ReasonResponse findById(Long id);

    List<ReasonResponse> findAllByUser(Long idUser);
    PageResponse<ReasonResponse> list(Boolean active, String search, Pageable pageable);

    void disable(Long id);
}
