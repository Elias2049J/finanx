package com.elias.mismonedas.catalog.service;

import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReasonResolver {

    private final ReasonRepository reasonRepository;
    private final UserRepository userRepository;

    public boolean hasInput(Long reasonId, String description) {
        return reasonId != null || (description != null && !description.isBlank());
    }

    @Transactional
    public Optional<Reason> resolveOrCreate(Long userId, Long reasonId, String description) {
        if (reasonId != null) {
            return Optional.of(reasonRepository.getReferenceById(reasonId));
        }

        if (description == null || description.isBlank()) {
            return Optional.empty();
        }

        if (userId == null) {
            throw new IllegalArgumentException("userId es requerido para crear un motivo");
        }

        Reason reason = new Reason();
        reason.setDescription(description.trim());
        reason.setUser(userRepository.getReferenceById(userId));

        return Optional.of(reasonRepository.save(reason));
    }
}

