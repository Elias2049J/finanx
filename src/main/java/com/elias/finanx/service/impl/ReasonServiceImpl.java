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
    private final ReasonRepository rRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ReasonResponse create(ReasonRequest request) {
        Reason reason = reasonMapper.toEntity(request);
        reason.setUser(userRepository.getReferenceById(request.getUserId()));
        return reasonMapper.toResponse(rRepository.save(reason));
    }

    @Override
    @Transactional
    public ReasonResponse update(Long id, ReasonRequest request) {
        Reason existing = rRepository.findById(id).orElseThrow();
        reasonMapper.updateFromDto(request, existing);
        return reasonMapper.toResponse(rRepository.save(existing));
    }

    @Override
    public ReasonResponse findById(Long id) {
        return reasonMapper.toResponse(rRepository.findById(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReasonResponse> findAllByUser(Long idUser) {
        return rRepository.findAllByUser_IdAndActiveTrue(idUser)
                .stream()
                .map(reasonMapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public void disable(Long id) {
        Reason existing = rRepository.findById(id).orElseThrow();
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setActive(false);
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        rRepository.save(existing);
    }
}
