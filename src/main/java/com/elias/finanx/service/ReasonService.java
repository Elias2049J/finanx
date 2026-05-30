package com.elias.finanx.service;

import com.elias.finanx.dto.reason.ReasonRequest;
import com.elias.finanx.dto.reason.ReasonResponseDTO;

import java.util.List;

public interface ReasonService {
    ReasonResponseDTO create(ReasonRequest request);

    ReasonResponseDTO update(Long id, ReasonRequest request);

    ReasonResponseDTO findById(Long id);

    List<ReasonResponseDTO> findAllByUser(Long idUser);

    void disable(Long id);
}
