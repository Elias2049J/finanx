package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.dto.user.UserPreferencesResponse;
import com.elias.mismonedas.identity.mapper.UserPreferencesMapper;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.identity.service.UserPreferencesService;
import com.elias.mismonedas.shared.exception.UserPreferencesNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesServiceImpl implements UserPreferencesService {

    private final UserPreferencesRepository repository;
    private final UserPreferencesMapper mapper;
    private final UserRepository userRepository;
    private final SecurityService securityService;

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse findByUserId() {
        Long userId = securityService.getCurrentUser().getId();
        UserPreferences prefs = repository.findByUser_Id(userId)
                .orElseThrow(() -> new UserPreferencesNotFoundException(userId));
        return mapper.toResponse(prefs);
    }

    @Override
    @Transactional
    public UserPreferencesResponse create(UserPreferencesRequest request) {
        return createFor(securityService.getCurrentUser().getId(), request);
    }

    private UserPreferencesResponse createFor(Long userId, UserPreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        return repository.findByUser_Id(userId)
                .map(existing -> {
                    log.info("Preferencias ya existen para userId={}, devolviendo existentes", userId);
                    return mapper.toResponse(existing);
                })
                .orElseGet(() -> {
                    UserPreferences prefs = mapper.toEntity(request);
                    prefs.setUser(user);
                    repository.save(prefs);
                    log.info("Preferencias creadas para userId={}", userId);
                    return mapper.toResponse(prefs);
                });
    }

    @Override
    @Transactional
    public UserPreferencesResponse update(UserPreferencesRequest request) {
        Long userId = securityService.getCurrentUser().getId();

        UserPreferences prefs = repository.findByUser_Id(userId)
                .orElseThrow(() -> new UserPreferencesNotFoundException(userId));

        mapper.updateFromDto(request, prefs);
        repository.save(prefs);
        log.info("Preferencias actualizadas para userId={}", userId);
        return mapper.toResponse(prefs);
    }
}
