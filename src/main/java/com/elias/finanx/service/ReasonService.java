package com.elias.finanx.service;

import com.elias.finanx.dto.reason.ReasonRequest;
import com.elias.finanx.dto.reason.ReasonResponse;

import java.util.List;

public interface ReasonService {
    ReasonResponse create(ReasonRequest request);

    ReasonResponse update(Long id, ReasonRequest request);

    ReasonResponse findById(Long id);

    List<ReasonResponse> findAllByUser(Long idUser);

    void disable(Long id);
}
