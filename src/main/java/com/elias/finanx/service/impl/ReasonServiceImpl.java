package com.elias.finanx.service.impl;

import com.elias.finanx.dto.reason.ReasonRequest;
import com.elias.finanx.dto.reason.ReasonResponse;
import com.elias.finanx.entity.Reason;
import com.elias.finanx.mapper.ReasonMapper;
import com.elias.finanx.repository.ReasonRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.ReasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
@Service

@RequiredArgsConstructor
public class ReasonServiceImpl implements ReasonService {
    private final ReasonMapper reasonMapper;
    private final ReasonRepository reasonRepository;
    private final UserRepository userRepository;

    @Override
    public ReasonResponse create(ReasonRequest request) {
        Reason reason = reasonMapper.toEntity(request);
        reason.setUser(userRepository.getReferenceById(request.getUserId()));
        return reasonMapper.toResponse(reasonRepository.save(reason));
    }

    @Override
    public ReasonResponse update(Long id, ReasonRequest request) {
        Reason existing = reasonRepository.findById(id).orElseThrow();
        reasonMapper.updateFromDto(request, existing);
        return reasonMapper.toResponse(reasonRepository.save(existing));
    }

    @Override
    public ReasonResponse findById(Long id) {
        return reasonMapper.toResponse(reasonRepository.findById(id).orElseThrow());
    }

    @Override
    public List<ReasonResponse> findAllByUser(Long idUser) {
        return reasonRepository.findAllByUser_Id(idUser)
                .stream()
                .map(reasonMapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public void disable(Long id) {
        Reason existing = reasonRepository.findById(id).orElseThrow();
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setActive(false);
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        reasonRepository.save(existing);
    }
}
